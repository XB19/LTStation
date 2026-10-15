package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.Clearinginfoes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClearinginfoesRepository extends JpaRepository<Clearinginfoes, Integer>, JpaSpecificationExecutor<Clearinginfoes> {
}
