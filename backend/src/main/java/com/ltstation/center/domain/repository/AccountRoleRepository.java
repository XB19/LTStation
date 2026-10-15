package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AccountRoleRepository extends JpaRepository<AccountRole, Integer>, JpaSpecificationExecutor<AccountRole> {
}
