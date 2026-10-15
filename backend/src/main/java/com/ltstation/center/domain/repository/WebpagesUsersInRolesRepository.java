package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.WebpagesUsersInRoles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebpagesUsersInRolesRepository extends JpaRepository<WebpagesUsersInRoles, WebpagesUsersInRoles.Pk>, JpaSpecificationExecutor<WebpagesUsersInRoles> {
}
