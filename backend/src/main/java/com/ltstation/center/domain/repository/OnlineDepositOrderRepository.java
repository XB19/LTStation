package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.OnlineDepositOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OnlineDepositOrderRepository extends JpaRepository<OnlineDepositOrder, Integer>, JpaSpecificationExecutor<OnlineDepositOrder> {
}
