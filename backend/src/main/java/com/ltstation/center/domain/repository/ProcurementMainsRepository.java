package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ProcurementMains;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProcurementMainsRepository extends JpaRepository<ProcurementMains, Integer>, JpaSpecificationExecutor<ProcurementMains> {
}
