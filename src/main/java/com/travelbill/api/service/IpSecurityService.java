package com.travelbill.api.service;

import com.travelbill.api.domain.IpBlacklistEntry;
import com.travelbill.api.repository.IpBlacklistEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IpSecurityService {
    private static final Duration DAY_WINDOW = Duration.ofHours(24);
    private static final String DAILY_COUNTER_PREFIX = "travel-aa-bill:ip:counter:";
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    private final Map<String, MinuteWindowCounter> minuteCounters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final IpBlacklistEntryRepository blacklistEntryRepository;
    private final int maxRequestsPerMinute;
    private final int maxRequestsPer24Hours;

    public IpSecurityService(
            StringRedisTemplate redisTemplate,
            IpBlacklistEntryRepository blacklistEntryRepository,
            @Value("${app.security.rate-limit.max-requests-per-minute:120}") int maxRequestsPerMinute,
            @Value("${app.security.ip-blacklist.max-requests-per-24-hours:500}") int maxRequestsPer24Hours
    ) {
        this.redisTemplate = redisTemplate;
        this.blacklistEntryRepository = blacklistEntryRepository;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxRequestsPer24Hours = maxRequestsPer24Hours;
    }

    public boolean exceedsMinuteLimit(String key) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        MinuteWindowCounter counter = minuteCounters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.minute != currentMinute) {
                return new MinuteWindowCounter(currentMinute);
            }
            return existing;
        });
        return counter.count.incrementAndGet() > maxRequestsPerMinute;
    }

    @Transactional
    public boolean isBlacklisted(String ip) {
        clearExpiredBlacklist();
        return blacklistEntryRepository.existsById(ip);
    }

    @Transactional
    public boolean recordAndCheckDailyLimit(String ip) {
        String counterKey = dailyCounterKey(ip);
        long nowMillis = System.currentTimeMillis();
        Map<Object, Object> counter = redisTemplate.opsForHash().entries(counterKey);

        long startedAtMillis = nowMillis;
        long currentCount = 1L;

        if (!counter.isEmpty()) {
            try {
                startedAtMillis = Long.parseLong(String.valueOf(counter.getOrDefault("startedAt", nowMillis)));
                currentCount = Long.parseLong(String.valueOf(counter.getOrDefault("count", 0))) + 1L;
            } catch (NumberFormatException ignored) {
                startedAtMillis = nowMillis;
                currentCount = 1L;
            }
        }

        if (nowMillis - startedAtMillis >= DAY_WINDOW.toMillis()) {
            startedAtMillis = nowMillis;
            currentCount = 1L;
        }

        redisTemplate.opsForHash().put(counterKey, "startedAt", String.valueOf(startedAtMillis));
        redisTemplate.opsForHash().put(counterKey, "count", String.valueOf(currentCount));
        redisTemplate.expire(counterKey, DAY_WINDOW);

        if (currentCount > maxRequestsPer24Hours) {
            LocalDateTime now = LocalDateTime.now();
            IpBlacklistEntry blacklistEntry = new IpBlacklistEntry();
            blacklistEntry.setIp(ip);
            blacklistEntry.setBlacklistedAt(now);
            blacklistEntry.setExpiresAt(now.plusHours(24));
            blacklistEntry.setRequestCount((int) currentCount);
            blacklistEntryRepository.save(blacklistEntry);

            redisTemplate.delete(counterKey);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<BlacklistView> listBlacklist() {
        return blacklistEntryRepository.findAllByOrderByExpiresAtAsc().stream()
                .map(entry -> new BlacklistView(
                        entry.getIp(),
                        toInstant(entry.getBlacklistedAt()),
                        toInstant(entry.getExpiresAt()),
                        entry.getRequestCount()
                ))
                .toList();
    }

    @Transactional
    public boolean removeFromBlacklist(String ip) {
        if (!blacklistEntryRepository.existsById(ip)) {
            return false;
        }

        blacklistEntryRepository.deleteById(ip);
        redisTemplate.delete(dailyCounterKey(ip));
        return true;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public int getMaxRequestsPer24Hours() {
        return maxRequestsPer24Hours;
    }

    private String dailyCounterKey(String ip) {
        return DAILY_COUNTER_PREFIX + ip;
    }

    @Transactional
    public void clearExpiredBlacklist() {
        blacklistEntryRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private Instant toInstant(LocalDateTime value) {
        return value.atZone(DEFAULT_ZONE).toInstant();
    }

    public record BlacklistView(
            String ip,
            Instant blacklistedAt,
            Instant expiresAt,
            int requestCount
    ) {
    }

    private static final class MinuteWindowCounter {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private MinuteWindowCounter(long minute) {
            this.minute = minute;
        }
    }
}
