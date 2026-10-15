package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationNozStatue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationNozStatueRepository extends JpaRepository<StationNozStatue, Integer>, JpaSpecificationExecutor<StationNozStatue> {
}
