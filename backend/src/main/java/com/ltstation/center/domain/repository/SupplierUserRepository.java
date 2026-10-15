package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierUserRepository extends JpaRepository<SupplierUser, Integer>, JpaSpecificationExecutor<SupplierUser> {
}
