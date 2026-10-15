package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Commodities;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommoditiesRepository extends JpaRepository<Commodities, Integer>, JpaSpecificationExecutor<Commodities> {
}
