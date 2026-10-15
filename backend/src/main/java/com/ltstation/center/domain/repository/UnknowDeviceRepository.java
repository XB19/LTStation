package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.UnknowDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UnknowDeviceRepository extends JpaRepository<UnknowDevice, Integer>, JpaSpecificationExecutor<UnknowDevice> {
}
