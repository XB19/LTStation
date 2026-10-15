package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.OperatorTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OperatorTypesRepository extends JpaRepository<OperatorTypes, Integer>, JpaSpecificationExecutor<OperatorTypes> {
}
