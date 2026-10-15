package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Genders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GendersRepository extends JpaRepository<Genders, Integer>, JpaSpecificationExecutor<Genders> {
}
