package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DutyGunInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DutyGunInfoRepository extends JpaRepository<DutyGunInfo, Integer>, JpaSpecificationExecutor<DutyGunInfo> {
}
