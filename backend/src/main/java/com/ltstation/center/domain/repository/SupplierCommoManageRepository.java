package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierCommoManage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierCommoManageRepository extends JpaRepository<SupplierCommoManage, Integer>, JpaSpecificationExecutor<SupplierCommoManage> {
}
