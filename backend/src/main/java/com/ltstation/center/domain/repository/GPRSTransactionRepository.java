package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSTransactionRepository extends JpaRepository<GPRSTransaction, Integer>, JpaSpecificationExecutor<GPRSTransaction> {
}
