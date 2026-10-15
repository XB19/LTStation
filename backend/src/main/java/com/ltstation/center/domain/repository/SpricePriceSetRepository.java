package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SpricePriceSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpricePriceSetRepository extends JpaRepository<SpricePriceSet, Integer>, JpaSpecificationExecutor<SpricePriceSet> {
}
