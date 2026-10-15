package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.JoyFillFleetCardRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JoyFillFleetCardRecordRepository extends JpaRepository<JoyFillFleetCardRecord, Integer>, JpaSpecificationExecutor<JoyFillFleetCardRecord> {
}
