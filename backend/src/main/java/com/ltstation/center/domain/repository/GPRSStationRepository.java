package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSStationRepository extends JpaRepository<GPRSStation, Integer>, JpaSpecificationExecutor<GPRSStation> {
}
