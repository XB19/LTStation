package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.BoxPayOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BoxPayOrderRepository extends JpaRepository<BoxPayOrder, Integer>, JpaSpecificationExecutor<BoxPayOrder> {
}
