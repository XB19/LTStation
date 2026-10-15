package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DailySalesReports;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DailySalesReportsRepository extends JpaRepository<DailySalesReports, Integer>, JpaSpecificationExecutor<DailySalesReports> {
}
