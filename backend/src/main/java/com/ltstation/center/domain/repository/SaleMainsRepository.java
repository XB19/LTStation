package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.SaleMains;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SaleMainsRepository extends JpaRepository<SaleMains, Integer>, JpaSpecificationExecutor<SaleMains> {
}
