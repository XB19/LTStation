package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.AirTank;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AirTankRepository extends JpaRepository<AirTank, Integer>, JpaSpecificationExecutor<AirTank> {

    List<AirTank> findByIsRunTrueAndStationIdIn(Collection<Integer> stationIds);
}
