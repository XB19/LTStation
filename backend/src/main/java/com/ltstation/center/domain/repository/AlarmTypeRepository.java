package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.AlarmType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlarmTypeRepository extends JpaRepository<AlarmType, Integer>, JpaSpecificationExecutor<AlarmType> {
}
