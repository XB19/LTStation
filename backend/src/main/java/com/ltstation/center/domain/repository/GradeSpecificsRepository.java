package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.GradeSpecifics;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GradeSpecificsRepository extends JpaRepository<GradeSpecifics, Integer>, JpaSpecificationExecutor<GradeSpecifics> {

    List<GradeSpecifics> findByCompanyIdOrderByGradeSpecificId(Integer companyId);

    Optional<GradeSpecifics> findFirstByOrderByCodeDesc();

    Optional<GradeSpecifics> findFirstByNameAndCompanyId(String name, Integer companyId);
}
