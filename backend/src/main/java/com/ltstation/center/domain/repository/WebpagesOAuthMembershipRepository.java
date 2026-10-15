package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.WebpagesOAuthMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebpagesOAuthMembershipRepository extends JpaRepository<WebpagesOAuthMembership, WebpagesOAuthMembership.Pk>, JpaSpecificationExecutor<WebpagesOAuthMembership> {
}
