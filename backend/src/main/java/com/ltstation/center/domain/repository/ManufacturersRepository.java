package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Manufacturers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ManufacturersRepository extends JpaRepository<Manufacturers, Integer>, JpaSpecificationExecutor<Manufacturers> {
}
