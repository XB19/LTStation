package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Regions;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RegionsRepository extends JpaRepository<Regions, Integer>, JpaSpecificationExecutor<Regions> {

    List<Regions> findByCompanyIdInOrderByRegionId(Collection<Integer> companyIds);

    Optional<Regions> findFirstByOrderByRegionIdDesc();

    Optional<Regions> findFirstByCodeOrRegionDesc(String code, String regionDesc);
}
