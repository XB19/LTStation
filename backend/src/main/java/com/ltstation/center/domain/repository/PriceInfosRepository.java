package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.PriceInfos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PriceInfosRepository extends JpaRepository<PriceInfos, Integer>, JpaSpecificationExecutor<PriceInfos> {
}
