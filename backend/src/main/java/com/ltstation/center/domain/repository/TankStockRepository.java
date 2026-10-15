package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TankStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TankStockRepository extends JpaRepository<TankStock, Integer>, JpaSpecificationExecutor<TankStock> {
}
