package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DailyGunReports;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DailyGunReportsRepository extends JpaRepository<DailyGunReports, Integer>, JpaSpecificationExecutor<DailyGunReports> {
}
