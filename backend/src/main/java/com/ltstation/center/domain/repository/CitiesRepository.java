package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Cities;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CitiesRepository extends JpaRepository<Cities, Integer>, JpaSpecificationExecutor<Cities> {

    List<Cities> findByCompanyIdInOrderByCityId(Collection<Integer> companyIds);

    List<Cities> findByRegionIdOrderByCityId(Integer regionId);

    boolean existsByRegionId(Integer regionId);

    Optional<Cities> findFirstByRegionIdOrderByCodeInnerDesc(Integer regionId);

    Optional<Cities> findFirstByCityDescAndRegionId(String cityDesc, Integer regionId);

    Optional<Cities> findFirstByCityDescAndCityIdNot(String cityDesc, Integer cityId);
}
