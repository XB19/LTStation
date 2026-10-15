package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSOilPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSOilPriceRepository extends JpaRepository<GPRSOilPrice, Integer>, JpaSpecificationExecutor<GPRSOilPrice> {
}
