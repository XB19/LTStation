package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierAdminRepository extends JpaRepository<SupplierAdmin, Integer>, JpaSpecificationExecutor<SupplierAdmin> {
}
