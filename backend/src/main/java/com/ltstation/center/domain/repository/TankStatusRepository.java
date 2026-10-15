package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TankStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TankStatusRepository extends JpaRepository<TankStatus, TankStatus.Pk>, JpaSpecificationExecutor<TankStatus> {
}
