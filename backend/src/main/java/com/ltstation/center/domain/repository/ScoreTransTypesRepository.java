package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ScoreTransTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ScoreTransTypesRepository extends JpaRepository<ScoreTransTypes, Integer>, JpaSpecificationExecutor<ScoreTransTypes> {
}
