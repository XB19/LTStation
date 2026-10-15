package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.MonitorDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MonitorDeviceRepository extends JpaRepository<MonitorDevice, Integer>, JpaSpecificationExecutor<MonitorDevice> {
}
