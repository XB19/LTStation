package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CreditCardBills;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CreditCardBillsRepository extends JpaRepository<CreditCardBills, Integer>, JpaSpecificationExecutor<CreditCardBills> {
}
