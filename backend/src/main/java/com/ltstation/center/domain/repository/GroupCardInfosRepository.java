package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GroupCardInfos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GroupCardInfosRepository extends JpaRepository<GroupCardInfos, String>, JpaSpecificationExecutor<GroupCardInfos> {
}
