package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CmdList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CmdListRepository extends JpaRepository<CmdList, Integer>, JpaSpecificationExecutor<CmdList> {
}
