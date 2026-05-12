package com.travelbill.api.repository;

import com.travelbill.api.domain.MemberStatus;
import com.travelbill.api.domain.PlanMember;
import com.travelbill.api.domain.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanMemberRepository extends JpaRepository<PlanMember, Long> {
    boolean existsByPlanIdAndUserId(Long planId, String userId);

    void deleteByPlan(TravelPlan plan);

    Optional<PlanMember> findByPlanIdAndUserId(Long planId, String userId);

    List<PlanMember> findByUserIdOrderByJoinedAtDesc(String userId);

    List<PlanMember> findByPlanOrderByJoinedAtAsc(TravelPlan plan);

    List<PlanMember> findByPlanAndStatusOrderByJoinedAtAsc(TravelPlan plan, MemberStatus status);

    Optional<PlanMember> findFirstByDisplayNameOrderByJoinedAtAsc(String displayName);

    long countByPlan(TravelPlan plan);
}
