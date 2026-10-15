package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.OnlineDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OnlineDepositRepository extends JpaRepository<OnlineDeposit, Integer>, JpaSpecificationExecutor<OnlineDeposit> {
}
