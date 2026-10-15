package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Stocktakings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StocktakingsRepository extends JpaRepository<Stocktakings, Integer>, JpaSpecificationExecutor<Stocktakings> {
}
