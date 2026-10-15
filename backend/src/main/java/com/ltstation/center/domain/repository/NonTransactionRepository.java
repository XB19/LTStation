package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.NonTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NonTransactionRepository extends JpaRepository<NonTransaction, Integer>, JpaSpecificationExecutor<NonTransaction> {
}
