package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TankHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TankHistoryRepository extends JpaRepository<TankHistory, Integer>, JpaSpecificationExecutor<TankHistory> {
}
