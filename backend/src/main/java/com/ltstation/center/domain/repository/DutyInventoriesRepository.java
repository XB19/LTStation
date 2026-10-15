package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DutyInventories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DutyInventoriesRepository extends JpaRepository<DutyInventories, Integer>, JpaSpecificationExecutor<DutyInventories> {
}
