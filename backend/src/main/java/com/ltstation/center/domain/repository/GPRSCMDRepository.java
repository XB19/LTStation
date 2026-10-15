package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GPRSCMD;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GPRSCMDRepository extends JpaRepository<GPRSCMD, Integer>, JpaSpecificationExecutor<GPRSCMD> {
}
