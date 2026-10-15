package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.AuthorityMess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuthorityMessRepository extends JpaRepository<AuthorityMess, Integer>, JpaSpecificationExecutor<AuthorityMess> {
}
