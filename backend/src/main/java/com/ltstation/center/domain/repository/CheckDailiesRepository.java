package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CheckDailies;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CheckDailiesRepository extends JpaRepository<CheckDailies, Integer>, JpaSpecificationExecutor<CheckDailies> {
}
