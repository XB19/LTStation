package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SupplierCommoRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierCommoRecordRepository extends JpaRepository<SupplierCommoRecord, Integer>, JpaSpecificationExecutor<SupplierCommoRecord> {
}
