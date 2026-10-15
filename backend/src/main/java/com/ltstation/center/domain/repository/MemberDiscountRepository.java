package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.MemberDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MemberDiscountRepository extends JpaRepository<MemberDiscount, Integer>, JpaSpecificationExecutor<MemberDiscount> {
}
