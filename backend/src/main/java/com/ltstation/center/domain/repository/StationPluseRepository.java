package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.StationPluse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StationPluseRepository extends JpaRepository<StationPluse, Integer>, JpaSpecificationExecutor<StationPluse> {
}
