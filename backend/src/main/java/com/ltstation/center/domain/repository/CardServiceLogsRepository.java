package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CardServiceLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CardServiceLogsRepository extends JpaRepository<CardServiceLogs, Integer>, JpaSpecificationExecutor<CardServiceLogs> {
}
