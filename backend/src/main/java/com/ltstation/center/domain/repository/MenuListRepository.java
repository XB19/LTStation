package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.MenuList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MenuListRepository extends JpaRepository<MenuList, Integer>, JpaSpecificationExecutor<MenuList> {
}
