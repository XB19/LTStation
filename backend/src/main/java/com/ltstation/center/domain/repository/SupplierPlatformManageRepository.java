package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierPlatformManage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierPlatformManageRepository extends JpaRepository<SupplierPlatformManage, Integer>, JpaSpecificationExecutor<SupplierPlatformManage> {
}
