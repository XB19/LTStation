package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Cylinder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CylinderRepository extends JpaRepository<Cylinder, Integer>, JpaSpecificationExecutor<Cylinder> {
}
