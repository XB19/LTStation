package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TransactionTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionTypesRepository extends JpaRepository<TransactionTypes, Integer>, JpaSpecificationExecutor<TransactionTypes> {
}
