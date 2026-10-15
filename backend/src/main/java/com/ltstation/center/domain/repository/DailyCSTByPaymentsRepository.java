package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DailyCSTByPayments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DailyCSTByPaymentsRepository extends JpaRepository<DailyCSTByPayments, Integer>, JpaSpecificationExecutor<DailyCSTByPayments> {
}
