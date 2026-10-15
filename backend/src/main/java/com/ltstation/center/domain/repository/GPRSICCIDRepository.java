package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSICCID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSICCIDRepository extends JpaRepository<GPRSICCID, Integer>, JpaSpecificationExecutor<GPRSICCID> {
}
