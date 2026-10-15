package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.AmountRadio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AmountRadioRepository extends JpaRepository<AmountRadio, Integer>, JpaSpecificationExecutor<AmountRadio> {
}
