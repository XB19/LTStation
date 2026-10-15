package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.NozStatue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NozStatueRepository extends JpaRepository<NozStatue, Integer>, JpaSpecificationExecutor<NozStatue> {
}
