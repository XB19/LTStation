package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.AirDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AirDeviceRepository extends JpaRepository<AirDevice, Integer>, JpaSpecificationExecutor<AirDevice> {
}
