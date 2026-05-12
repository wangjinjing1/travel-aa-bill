package com.travelbill.api.repository;

import com.travelbill.api.domain.RegistrationInvite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationInviteRepository extends JpaRepository<RegistrationInvite, String> {
}
