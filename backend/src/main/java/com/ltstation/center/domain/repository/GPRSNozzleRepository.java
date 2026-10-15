package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSNozzle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSNozzleRepository extends JpaRepository<GPRSNozzle, Integer>, JpaSpecificationExecutor<GPRSNozzle> {
}
