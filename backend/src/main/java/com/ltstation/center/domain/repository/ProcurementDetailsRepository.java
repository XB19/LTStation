package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ProcurementDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProcurementDetailsRepository extends JpaRepository<ProcurementDetails, ProcurementDetails.Pk>, JpaSpecificationExecutor<ProcurementDetails> {
}
