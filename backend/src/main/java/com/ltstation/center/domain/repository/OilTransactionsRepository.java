package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.OilTransactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OilTransactionsRepository extends JpaRepository<OilTransactions, Integer>, JpaSpecificationExecutor<OilTransactions> {
}
