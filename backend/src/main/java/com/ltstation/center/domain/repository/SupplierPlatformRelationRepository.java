package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierPlatformRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierPlatformRelationRepository extends JpaRepository<SupplierPlatformRelation, Integer>, JpaSpecificationExecutor<SupplierPlatformRelation> {
}
