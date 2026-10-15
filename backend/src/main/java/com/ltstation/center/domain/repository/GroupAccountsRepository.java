package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GroupAccounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GroupAccountsRepository extends JpaRepository<GroupAccounts, Integer>, JpaSpecificationExecutor<GroupAccounts> {
}
