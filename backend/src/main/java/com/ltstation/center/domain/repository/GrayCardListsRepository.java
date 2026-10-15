package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GrayCardLists;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GrayCardListsRepository extends JpaRepository<GrayCardLists, Integer>, JpaSpecificationExecutor<GrayCardLists> {
}
