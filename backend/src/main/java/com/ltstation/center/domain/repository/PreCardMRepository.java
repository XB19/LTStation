package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.PreCardM;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PreCardMRepository extends JpaRepository<PreCardM, Integer>, JpaSpecificationExecutor<PreCardM> {
}
