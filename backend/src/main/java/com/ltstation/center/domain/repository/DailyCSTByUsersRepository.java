package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DailyCSTByUsers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DailyCSTByUsersRepository extends JpaRepository<DailyCSTByUsers, Integer>, JpaSpecificationExecutor<DailyCSTByUsers> {
}
