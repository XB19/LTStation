package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table score_settings (origine SQL Server : ScoreSettings). */
@Getter
@Setter
@Entity
@Table(name = "score_settings")
@IdClass(ScoreSettings.Pk.class)
public class ScoreSettings {

    @Id
    @Column(name = "group_id")
    private Integer groupId;

    @Id
    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "ratio")
    private Double ratio;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer groupId;
        private Integer gradeSpecificId;
    }
}
