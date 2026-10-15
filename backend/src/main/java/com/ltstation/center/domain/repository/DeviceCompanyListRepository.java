package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.DeviceCompanyList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeviceCompanyListRepository extends JpaRepository<DeviceCompanyList, Integer>, JpaSpecificationExecutor<DeviceCompanyList> {
}
