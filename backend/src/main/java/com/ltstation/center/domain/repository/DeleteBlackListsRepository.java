package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DeleteBlackLists;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeleteBlackListsRepository extends JpaRepository<DeleteBlackLists, Integer>, JpaSpecificationExecutor<DeleteBlackLists> {
}
