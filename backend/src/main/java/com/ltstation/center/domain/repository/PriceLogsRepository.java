package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.PriceLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PriceLogsRepository extends JpaRepository<PriceLogs, Integer>, JpaSpecificationExecutor<PriceLogs> {
}
