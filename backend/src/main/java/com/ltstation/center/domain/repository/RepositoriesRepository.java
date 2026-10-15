package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RepositoriesRepository extends JpaRepository<Repositories, Integer>, JpaSpecificationExecutor<Repositories> {
}
