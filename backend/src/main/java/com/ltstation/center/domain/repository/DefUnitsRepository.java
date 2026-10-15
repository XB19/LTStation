package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefUnits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefUnitsRepository extends JpaRepository<DefUnits, Integer>, JpaSpecificationExecutor<DefUnits> {
}
