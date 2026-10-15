package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TransportInfoes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransportInfoesRepository extends JpaRepository<TransportInfoes, Integer>, JpaSpecificationExecutor<TransportInfoes> {
}
