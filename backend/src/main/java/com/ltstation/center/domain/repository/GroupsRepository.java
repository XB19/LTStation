package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Groups;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GroupsRepository extends JpaRepository<Groups, Integer>, JpaSpecificationExecutor<Groups> {
}
