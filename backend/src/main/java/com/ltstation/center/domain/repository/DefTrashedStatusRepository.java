package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefTrashedStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefTrashedStatusRepository extends JpaRepository<DefTrashedStatus, Integer>, JpaSpecificationExecutor<DefTrashedStatus> {
}
