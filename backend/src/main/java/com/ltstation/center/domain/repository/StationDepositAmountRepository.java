package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationDepositAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationDepositAmountRepository extends JpaRepository<StationDepositAmount, Integer>, JpaSpecificationExecutor<StationDepositAmount> {
}
