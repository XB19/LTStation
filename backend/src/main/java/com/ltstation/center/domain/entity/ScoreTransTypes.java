package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table score_trans_types (origine SQL Server : ScoreTransTypes). */
@Getter
@Setter
@Entity
@Table(name = "score_trans_types")
public class ScoreTransTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_trans_type_id")
    private Integer scoreTransTypeId;

    @Column(name = "name")
    private String name;

    @Column(name = "name_en")
    private String nameEn;

}
