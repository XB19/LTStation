package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ScoreDepositRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ScoreDepositRateRepository extends JpaRepository<ScoreDepositRate, Integer>, JpaSpecificationExecutor<ScoreDepositRate> {
}
