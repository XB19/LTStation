package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationTankStatue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationTankStatueRepository extends JpaRepository<StationTankStatue, Integer>, JpaSpecificationExecutor<StationTankStatue> {
}
