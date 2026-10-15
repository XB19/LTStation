package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.WebpagesRoles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebpagesRolesRepository extends JpaRepository<WebpagesRoles, Integer>, JpaSpecificationExecutor<WebpagesRoles> {
}
