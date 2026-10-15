package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SaleDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SaleDetailsRepository extends JpaRepository<SaleDetails, SaleDetails.Pk>, JpaSpecificationExecutor<SaleDetails> {
}
