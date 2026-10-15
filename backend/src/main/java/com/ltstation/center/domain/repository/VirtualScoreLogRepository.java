package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.VirtualScoreLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VirtualScoreLogRepository extends JpaRepository<VirtualScoreLog, Integer>, JpaSpecificationExecutor<VirtualScoreLog> {
}
