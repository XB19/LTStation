package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.VehicleColors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VehicleColorsRepository extends JpaRepository<VehicleColors, Integer>, JpaSpecificationExecutor<VehicleColors> {
}
