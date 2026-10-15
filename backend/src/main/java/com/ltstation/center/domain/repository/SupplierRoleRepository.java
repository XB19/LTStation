package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierRoleRepository extends JpaRepository<SupplierRole, Integer>, JpaSpecificationExecutor<SupplierRole> {
}
