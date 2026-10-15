package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierOilSupply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierOilSupplyRepository extends JpaRepository<SupplierOilSupply, Integer>, JpaSpecificationExecutor<SupplierOilSupply> {
}
