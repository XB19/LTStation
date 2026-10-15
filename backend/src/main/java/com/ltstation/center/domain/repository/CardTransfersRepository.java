package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CardTransfers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CardTransfersRepository extends JpaRepository<CardTransfers, Integer>, JpaSpecificationExecutor<CardTransfers> {
}
