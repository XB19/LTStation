package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.JoyFillRenewOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JoyFillRenewOrderRepository extends JpaRepository<JoyFillRenewOrder, Integer>, JpaSpecificationExecutor<JoyFillRenewOrder> {
}
