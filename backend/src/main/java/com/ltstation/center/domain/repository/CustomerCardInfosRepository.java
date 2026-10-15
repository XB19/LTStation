package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CustomerCardInfos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerCardInfosRepository extends JpaRepository<CustomerCardInfos, String>, JpaSpecificationExecutor<CustomerCardInfos> {
}
