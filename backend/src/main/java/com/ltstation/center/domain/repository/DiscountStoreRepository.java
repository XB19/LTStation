package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DiscountStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiscountStoreRepository extends JpaRepository<DiscountStore, DiscountStore.Pk>, JpaSpecificationExecutor<DiscountStore> {
}
