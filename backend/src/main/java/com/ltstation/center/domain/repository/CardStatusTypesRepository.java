package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CardStatusTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CardStatusTypesRepository extends JpaRepository<CardStatusTypes, Integer>, JpaSpecificationExecutor<CardStatusTypes> {
}
