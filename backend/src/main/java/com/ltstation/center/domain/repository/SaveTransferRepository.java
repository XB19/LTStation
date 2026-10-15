package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SaveTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SaveTransferRepository extends JpaRepository<SaveTransfer, Integer>, JpaSpecificationExecutor<SaveTransfer> {
}
