package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TankRealData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TankRealDataRepository extends JpaRepository<TankRealData, Integer>, JpaSpecificationExecutor<TankRealData> {
}
