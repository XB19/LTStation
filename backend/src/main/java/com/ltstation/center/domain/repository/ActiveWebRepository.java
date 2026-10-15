package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ActiveWeb;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActiveWebRepository extends JpaRepository<ActiveWeb, Integer>, JpaSpecificationExecutor<ActiveWeb> {

    Optional<ActiveWeb> findFirstByOrderByIdAsc();
}
