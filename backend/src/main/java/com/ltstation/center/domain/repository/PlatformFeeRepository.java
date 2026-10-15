package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.PlatformFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformFeeRepository extends JpaRepository<PlatformFee, Integer>, JpaSpecificationExecutor<PlatformFee> {
}
