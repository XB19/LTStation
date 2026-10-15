package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ADContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ADContentRepository extends JpaRepository<ADContent, Integer>, JpaSpecificationExecutor<ADContent> {
}
