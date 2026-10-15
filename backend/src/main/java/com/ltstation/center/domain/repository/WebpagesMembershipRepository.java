package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.WebpagesMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebpagesMembershipRepository extends JpaRepository<WebpagesMembership, Integer>, JpaSpecificationExecutor<WebpagesMembership> {
}
