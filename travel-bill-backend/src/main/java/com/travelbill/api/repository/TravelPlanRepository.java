package com.travelbill.api.repository;

import com.travelbill.api.domain.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {
    Optional<TravelPlan> findByIdAndShareToken(Long id, String shareToken);

    Optional<TravelPlan> findByCreatorIdAndRequestId(String creatorId, String requestId);

    Optional<TravelPlan> findFirstByCreatorNameOrderByCreatedAtAsc(String creatorName);

    List<TravelPlan> findByCreatorId(String creatorId);
}
