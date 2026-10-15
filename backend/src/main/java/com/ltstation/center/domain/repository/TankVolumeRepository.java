package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.TankVolume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TankVolumeRepository extends JpaRepository<TankVolume, Integer>, JpaSpecificationExecutor<TankVolume> {
}
