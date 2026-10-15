package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Inventories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InventoriesRepository extends JpaRepository<Inventories, Inventories.Pk>, JpaSpecificationExecutor<Inventories> {
}
