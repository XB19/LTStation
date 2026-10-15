package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.EmpScoreRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmpScoreRateRepository extends JpaRepository<EmpScoreRate, Integer>, JpaSpecificationExecutor<EmpScoreRate> {
}
