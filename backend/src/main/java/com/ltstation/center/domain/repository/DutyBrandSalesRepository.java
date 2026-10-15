package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DutyBrandSales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DutyBrandSalesRepository extends JpaRepository<DutyBrandSales, DutyBrandSales.Pk>, JpaSpecificationExecutor<DutyBrandSales> {
}
