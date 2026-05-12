package com.travelbill.api.repository;

import com.travelbill.api.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByDisplayName(String displayName);

    Optional<AppUser> findByOpenId(String openId);

    Optional<AppUser> findByUsername(String username);
}
