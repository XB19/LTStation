package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.JoyFillTankAlarrmData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JoyFillTankAlarrmDataRepository extends JpaRepository<JoyFillTankAlarrmData, Integer>, JpaSpecificationExecutor<JoyFillTankAlarrmData> {
}
