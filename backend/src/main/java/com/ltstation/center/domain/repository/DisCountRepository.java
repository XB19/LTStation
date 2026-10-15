package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DisCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DisCountRepository extends JpaRepository<DisCount, Integer>, JpaSpecificationExecutor<DisCount> {
}
