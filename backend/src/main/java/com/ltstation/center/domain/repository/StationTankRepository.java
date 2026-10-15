package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationTank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationTankRepository extends JpaRepository<StationTank, Integer>, JpaSpecificationExecutor<StationTank> {
}
