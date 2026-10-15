package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefPayments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefPaymentsRepository extends JpaRepository<DefPayments, Integer>, JpaSpecificationExecutor<DefPayments> {
}
