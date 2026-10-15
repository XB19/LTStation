package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DistributionPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DistributionPointsRepository extends JpaRepository<DistributionPoints, Integer>, JpaSpecificationExecutor<DistributionPoints> {
}
