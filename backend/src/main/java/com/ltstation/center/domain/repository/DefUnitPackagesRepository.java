package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefUnitPackages;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefUnitPackagesRepository extends JpaRepository<DefUnitPackages, Integer>, JpaSpecificationExecutor<DefUnitPackages> {
}
