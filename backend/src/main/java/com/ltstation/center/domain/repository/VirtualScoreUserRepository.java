package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.VirtualScoreUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VirtualScoreUserRepository extends JpaRepository<VirtualScoreUser, Integer>, JpaSpecificationExecutor<VirtualScoreUser> {
}
