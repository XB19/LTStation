package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefCategories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefCategoriesRepository extends JpaRepository<DefCategories, Integer>, JpaSpecificationExecutor<DefCategories> {
}
