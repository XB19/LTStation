package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CylinderTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CylinderTypesRepository extends JpaRepository<CylinderTypes, Integer>, JpaSpecificationExecutor<CylinderTypes> {
}
