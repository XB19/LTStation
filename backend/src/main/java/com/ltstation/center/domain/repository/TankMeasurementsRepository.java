package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TankMeasurements;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TankMeasurementsRepository extends JpaRepository<TankMeasurements, Integer>, JpaSpecificationExecutor<TankMeasurements> {
}
