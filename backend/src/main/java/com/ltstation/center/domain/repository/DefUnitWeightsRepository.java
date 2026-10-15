package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefUnitWeights;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefUnitWeightsRepository extends JpaRepository<DefUnitWeights, Integer>, JpaSpecificationExecutor<DefUnitWeights> {
}
