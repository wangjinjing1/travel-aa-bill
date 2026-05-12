package com.travelbill.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthTokenService {
    private final String secret;
    private final long ttlSeconds;

    public AuthTokenService(
            @Value("${app.auth.token-secret}") String secret,
            @Value("${app.auth.token-ttl-hours:168}") long ttlHours
    ) {
        this.secret = secret;
        this.ttlSeconds = ttlHours * 3600;
    }

    public String issue(String userId) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = base64Url(userId + ":" + expiresAt);
        String signature = sign(payload);
        return payload + "." + signature;
    }

    public String verify(String token) {
        if (token == null || !token.contains(".")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String[] parts = token.split("\\.", 2);
        if (!constantTimeEquals(sign(parts[0]), parts[1])) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录状态无效");
        }
        String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        int split = decoded.lastIndexOf(':');
        if (split <= 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录状态无效");
        }
        long expiresAt = Long.parseLong(decoded.substring(split + 1));
        if (expiresAt < Instant.now().getEpochSecond()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录已过期");
        }
        return decoded.substring(0, split);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign token", exception);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) return false;
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }
}
