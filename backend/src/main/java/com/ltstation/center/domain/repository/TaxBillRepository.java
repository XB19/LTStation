package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TaxBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaxBillRepository extends JpaRepository<TaxBill, Integer>, JpaSpecificationExecutor<TaxBill> {
}
