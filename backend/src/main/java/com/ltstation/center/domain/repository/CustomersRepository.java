package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Customers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomersRepository extends JpaRepository<Customers, Integer>, JpaSpecificationExecutor<Customers> {
}
