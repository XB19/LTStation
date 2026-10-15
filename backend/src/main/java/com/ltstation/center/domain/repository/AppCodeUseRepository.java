package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.AppCodeUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppCodeUseRepository extends JpaRepository<AppCodeUse, Integer>, JpaSpecificationExecutor<AppCodeUse> {
}
