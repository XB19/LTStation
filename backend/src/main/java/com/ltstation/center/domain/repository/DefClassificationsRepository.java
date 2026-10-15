package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefClassifications;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefClassificationsRepository extends JpaRepository<DefClassifications, Integer>, JpaSpecificationExecutor<DefClassifications> {
}
