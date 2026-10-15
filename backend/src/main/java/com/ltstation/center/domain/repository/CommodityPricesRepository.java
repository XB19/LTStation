package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CommodityPrices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommodityPricesRepository extends JpaRepository<CommodityPrices, CommodityPrices.Pk>, JpaSpecificationExecutor<CommodityPrices> {
}
