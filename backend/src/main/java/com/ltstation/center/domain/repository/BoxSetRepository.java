package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.BoxSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BoxSetRepository extends JpaRepository<BoxSet, Integer>, JpaSpecificationExecutor<BoxSet> {
}
