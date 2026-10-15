package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.MonthlyGunReports;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MonthlyGunReportsRepository extends JpaRepository<MonthlyGunReports, Integer>, JpaSpecificationExecutor<MonthlyGunReports> {
}
