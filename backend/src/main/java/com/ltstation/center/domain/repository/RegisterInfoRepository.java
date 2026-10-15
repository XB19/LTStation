package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.RegisterInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RegisterInfoRepository extends JpaRepository<RegisterInfo, Integer>, JpaSpecificationExecutor<RegisterInfo> {
}
