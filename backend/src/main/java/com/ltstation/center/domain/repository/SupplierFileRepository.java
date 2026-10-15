package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierFileRepository extends JpaRepository<SupplierFile, Integer>, JpaSpecificationExecutor<SupplierFile> {
}
