package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Locations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LocationsRepository extends JpaRepository<Locations, Locations.Pk>, JpaSpecificationExecutor<Locations> {
}
