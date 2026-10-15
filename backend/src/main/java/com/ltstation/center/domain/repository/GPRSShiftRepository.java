package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSShiftRepository extends JpaRepository<GPRSShift, Integer>, JpaSpecificationExecutor<GPRSShift> {
}
