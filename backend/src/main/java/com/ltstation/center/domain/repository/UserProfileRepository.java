package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserProfileRepository extends JpaRepository<UserProfile, Integer>, JpaSpecificationExecutor<UserProfile> {
}
