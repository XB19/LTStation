package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.AppendBlackLists;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppendBlackListsRepository extends JpaRepository<AppendBlackLists, Integer>, JpaSpecificationExecutor<AppendBlackLists> {
}
