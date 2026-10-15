package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CompanyManagements;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyManagementsRepository extends JpaRepository<CompanyManagements, Integer>, JpaSpecificationExecutor<CompanyManagements> {
}
