package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Apps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppsRepository extends JpaRepository<Apps, Integer>, JpaSpecificationExecutor<Apps> {
}
