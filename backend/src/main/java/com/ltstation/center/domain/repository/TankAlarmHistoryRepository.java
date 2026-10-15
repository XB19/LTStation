package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TankAlarmHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TankAlarmHistoryRepository extends JpaRepository<TankAlarmHistory, Integer>, JpaSpecificationExecutor<TankAlarmHistory> {
}
