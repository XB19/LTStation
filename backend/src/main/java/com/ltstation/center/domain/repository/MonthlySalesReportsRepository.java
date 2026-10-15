package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.MonthlySalesReports;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MonthlySalesReportsRepository extends JpaRepository<MonthlySalesReports, Integer>, JpaSpecificationExecutor<MonthlySalesReports> {
}
