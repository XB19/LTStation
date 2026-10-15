package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.MonthInventories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MonthInventoriesRepository extends JpaRepository<MonthInventories, Integer>, JpaSpecificationExecutor<MonthInventories> {
}
