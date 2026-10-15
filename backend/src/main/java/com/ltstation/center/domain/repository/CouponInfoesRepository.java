package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CouponInfoes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CouponInfoesRepository extends JpaRepository<CouponInfoes, Integer>, JpaSpecificationExecutor<CouponInfoes> {
}
