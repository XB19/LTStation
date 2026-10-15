package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.PrintTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PrintTableRepository extends JpaRepository<PrintTable, Integer>, JpaSpecificationExecutor<PrintTable> {
}
