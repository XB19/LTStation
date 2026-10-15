package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.JoyFillFleetRechargeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JoyFillFleetRechargeRecordRepository extends JpaRepository<JoyFillFleetRechargeRecord, Integer>, JpaSpecificationExecutor<JoyFillFleetRechargeRecord> {
}
