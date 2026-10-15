package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.JoyFillFleetDriver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JoyFillFleetDriverRepository extends JpaRepository<JoyFillFleetDriver, Integer>, JpaSpecificationExecutor<JoyFillFleetDriver> {
}
