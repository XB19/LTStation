package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.BoxPortSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BoxPortSetRepository extends JpaRepository<BoxPortSet, Integer>, JpaSpecificationExecutor<BoxPortSet> {
}
