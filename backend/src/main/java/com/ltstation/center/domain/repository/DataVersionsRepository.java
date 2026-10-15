package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DataVersions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DataVersionsRepository extends JpaRepository<DataVersions, Integer>, JpaSpecificationExecutor<DataVersions> {
}
