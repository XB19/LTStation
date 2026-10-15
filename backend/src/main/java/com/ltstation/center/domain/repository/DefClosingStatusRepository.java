package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefClosingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefClosingStatusRepository extends JpaRepository<DefClosingStatus, Integer>, JpaSpecificationExecutor<DefClosingStatus> {
}
