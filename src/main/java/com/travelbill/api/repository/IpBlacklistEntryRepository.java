package com.travelbill.api.repository;

import com.travelbill.api.domain.IpBlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IpBlacklistEntryRepository extends JpaRepository<IpBlacklistEntry, String> {
    List<IpBlacklistEntry> findAllByOrderByExpiresAtAsc();

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
