package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Stations;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface StationsRepository extends JpaRepository<Stations, Integer>, JpaSpecificationExecutor<Stations> {

    List<Stations> findByCompanyIdIn(Collection<Integer> companyIds);

    @Query("select s.stationId from Stations s where s.companyId in :companyIds")
    List<Integer> findStationIdsByCompanyIdIn(Collection<Integer> companyIds);

    List<Stations> findByCityId(Integer cityId);

    boolean existsByCityId(Integer cityId);

    List<Stations> findByCompanyIdInAndIsActiveOrderByStationId(Collection<Integer> companyIds, Integer isActive);

    @Query("select max(s.code) from Stations s")
    String findMaxCode();

    java.util.Optional<Stations> findFirstByOrderByStationIdDesc();

    @Query(value = "select s.station_id from stations s join cities c on c.city_id = s.city_id where c.region_id = :regionId",
            nativeQuery = true)
    List<Integer> findStationIdsByRegionId(Integer regionId);
}
