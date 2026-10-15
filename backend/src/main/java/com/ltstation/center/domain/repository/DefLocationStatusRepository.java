package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefLocationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefLocationStatusRepository extends JpaRepository<DefLocationStatus, Integer>, JpaSpecificationExecutor<DefLocationStatus> {
}
