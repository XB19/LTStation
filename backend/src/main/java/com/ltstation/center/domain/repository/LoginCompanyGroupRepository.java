package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.LoginCompanyGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LoginCompanyGroupRepository extends JpaRepository<LoginCompanyGroup, Integer>, JpaSpecificationExecutor<LoginCompanyGroup> {

    List<LoginCompanyGroup> findByGroupId(Integer groupId);
}
