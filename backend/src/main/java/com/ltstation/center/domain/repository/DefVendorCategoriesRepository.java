package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DefVendorCategories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DefVendorCategoriesRepository extends JpaRepository<DefVendorCategories, Integer>, JpaSpecificationExecutor<DefVendorCategories> {
}
