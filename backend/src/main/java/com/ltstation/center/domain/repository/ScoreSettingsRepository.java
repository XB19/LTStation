package com.ltstation.center.domain.repository;

import com.ltstation.center.domain.entity.ScoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ScoreSettingsRepository extends JpaRepository<ScoreSettings, ScoreSettings.Pk>, JpaSpecificationExecutor<ScoreSettings> {
}
