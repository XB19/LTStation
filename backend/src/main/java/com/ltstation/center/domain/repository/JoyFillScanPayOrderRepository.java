package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.JoyFillScanPayOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JoyFillScanPayOrderRepository extends JpaRepository<JoyFillScanPayOrder, Integer>, JpaSpecificationExecutor<JoyFillScanPayOrder> {
}
