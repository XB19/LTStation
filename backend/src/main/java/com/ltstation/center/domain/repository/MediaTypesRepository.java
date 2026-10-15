package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.MediaTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MediaTypesRepository extends JpaRepository<MediaTypes, Integer>, JpaSpecificationExecutor<MediaTypes> {
}
