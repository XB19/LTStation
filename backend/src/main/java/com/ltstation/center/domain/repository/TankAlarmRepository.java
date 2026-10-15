package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TankAlarm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TankAlarmRepository extends JpaRepository<TankAlarm, Integer>, JpaSpecificationExecutor<TankAlarm> {
}
