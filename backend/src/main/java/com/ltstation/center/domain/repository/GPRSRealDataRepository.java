package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSRealData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSRealDataRepository extends JpaRepository<GPRSRealData, Integer>, JpaSpecificationExecutor<GPRSRealData> {
}
