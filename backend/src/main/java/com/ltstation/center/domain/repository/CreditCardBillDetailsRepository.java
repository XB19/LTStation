package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CreditCardBillDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CreditCardBillDetailsRepository extends JpaRepository<CreditCardBillDetails, CreditCardBillDetails.Pk>, JpaSpecificationExecutor<CreditCardBillDetails> {
}
