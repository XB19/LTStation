package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationNoz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationNozRepository extends JpaRepository<StationNoz, Integer>, JpaSpecificationExecutor<StationNoz> {
}
