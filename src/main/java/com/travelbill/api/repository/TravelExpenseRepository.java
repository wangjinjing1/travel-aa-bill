package com.travelbill.api.repository;

import com.travelbill.api.domain.TravelExpense;
import com.travelbill.api.domain.TravelPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TravelExpenseRepository extends JpaRepository<TravelExpense, Long>, JpaSpecificationExecutor<TravelExpense> {
    List<TravelExpense> findByPlanOrderBySpentAtDescCreatedAtDesc(TravelPlan plan);

    void deleteByPlan(TravelPlan plan);

    void deleteByUserId(String userId);

    Page<TravelExpense> findByPlan(TravelPlan plan, Pageable pageable);

    Optional<TravelExpense> findByPlanIdAndUserIdAndRequestId(Long planId, String userId, String requestId);

    List<TravelExpense> findByPayerNameOrderByCreatedAtDesc(String payerName);

    List<TravelExpense> findByUserIdOrderByCreatedAtDesc(String userId);
}
