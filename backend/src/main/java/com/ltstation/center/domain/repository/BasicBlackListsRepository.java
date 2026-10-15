package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.BasicBlackLists;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BasicBlackListsRepository extends JpaRepository<BasicBlackLists, Integer>, JpaSpecificationExecutor<BasicBlackLists> {
}
