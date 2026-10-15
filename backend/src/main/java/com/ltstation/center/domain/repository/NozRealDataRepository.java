package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.NozRealData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NozRealDataRepository extends JpaRepository<NozRealData, Integer>, JpaSpecificationExecutor<NozRealData> {
}
