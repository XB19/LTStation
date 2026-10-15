package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.JoyFillMerchInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JoyFillMerchInfoRepository extends JpaRepository<JoyFillMerchInfo, Integer>, JpaSpecificationExecutor<JoyFillMerchInfo> {
}
