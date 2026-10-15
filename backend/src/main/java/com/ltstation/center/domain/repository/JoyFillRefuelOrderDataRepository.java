package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.JoyFillRefuelOrderData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JoyFillRefuelOrderDataRepository extends JpaRepository<JoyFillRefuelOrderData, Integer>, JpaSpecificationExecutor<JoyFillRefuelOrderData> {
}
