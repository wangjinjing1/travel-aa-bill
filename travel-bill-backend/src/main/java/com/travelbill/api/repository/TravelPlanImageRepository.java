package com.travelbill.api.repository;

import com.travelbill.api.domain.TravelPlan;
import com.travelbill.api.domain.TravelPlanImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelPlanImageRepository extends JpaRepository<TravelPlanImage, Long> {
    List<TravelPlanImage> findByPlanOrderByCreatedAtAsc(TravelPlan plan);

    void deleteByPlan(TravelPlan plan);
}
