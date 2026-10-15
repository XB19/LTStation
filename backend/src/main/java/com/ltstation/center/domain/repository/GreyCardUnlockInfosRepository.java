package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GreyCardUnlockInfos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GreyCardUnlockInfosRepository extends JpaRepository<GreyCardUnlockInfos, Integer>, JpaSpecificationExecutor<GreyCardUnlockInfos> {
}
