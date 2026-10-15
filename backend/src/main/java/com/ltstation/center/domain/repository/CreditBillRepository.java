package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CreditBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CreditBillRepository extends JpaRepository<CreditBill, Integer>, JpaSpecificationExecutor<CreditBill> {
}
