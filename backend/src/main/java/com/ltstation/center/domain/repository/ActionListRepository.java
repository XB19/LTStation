package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ActionList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActionListRepository extends JpaRepository<ActionList, Integer>, JpaSpecificationExecutor<ActionList> {
}
