package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.PumpsProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PumpsProfileRepository extends JpaRepository<PumpsProfile, PumpsProfile.Pk>, JpaSpecificationExecutor<PumpsProfile> {
}
