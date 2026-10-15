package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationDepositLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationDepositLogRepository extends JpaRepository<StationDepositLog, Integer>, JpaSpecificationExecutor<StationDepositLog> {
}
