package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefCurrencies;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefCurrenciesRepository extends JpaRepository<DefCurrencies, Integer>, JpaSpecificationExecutor<DefCurrencies> {
}
