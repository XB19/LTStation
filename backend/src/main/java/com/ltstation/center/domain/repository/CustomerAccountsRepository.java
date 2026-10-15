package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CustomerAccounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerAccountsRepository extends JpaRepository<CustomerAccounts, Integer>, JpaSpecificationExecutor<CustomerAccounts> {
}
