package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierPurchaseRequestBuy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierPurchaseRequestBuyRepository extends JpaRepository<SupplierPurchaseRequestBuy, Integer>, JpaSpecificationExecutor<SupplierPurchaseRequestBuy> {
}
