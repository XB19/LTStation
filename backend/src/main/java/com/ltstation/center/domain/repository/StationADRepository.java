package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationAD;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationADRepository extends JpaRepository<StationAD, Integer>, JpaSpecificationExecutor<StationAD> {
}
