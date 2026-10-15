package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationTypesRepository extends JpaRepository<StationTypes, Integer>, JpaSpecificationExecutor<StationTypes> {
}
