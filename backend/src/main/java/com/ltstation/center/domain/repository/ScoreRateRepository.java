package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ScoreRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ScoreRateRepository extends JpaRepository<ScoreRate, Integer>, JpaSpecificationExecutor<ScoreRate> {
}
