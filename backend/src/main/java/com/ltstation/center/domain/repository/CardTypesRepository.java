package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CardTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CardTypesRepository extends JpaRepository<CardTypes, Integer>, JpaSpecificationExecutor<CardTypes> {
}
