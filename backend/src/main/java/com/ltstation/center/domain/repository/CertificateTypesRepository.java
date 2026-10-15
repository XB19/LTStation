package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.CertificateTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CertificateTypesRepository extends JpaRepository<CertificateTypes, Integer>, JpaSpecificationExecutor<CertificateTypes> {
}
