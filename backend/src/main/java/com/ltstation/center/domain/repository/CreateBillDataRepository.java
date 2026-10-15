package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CreateBillData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CreateBillDataRepository extends JpaRepository<CreateBillData, Integer>, JpaSpecificationExecutor<CreateBillData> {
}
