package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.EmployeeCardInfos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmployeeCardInfosRepository extends JpaRepository<EmployeeCardInfos, String>, JpaSpecificationExecutor<EmployeeCardInfos> {
}
