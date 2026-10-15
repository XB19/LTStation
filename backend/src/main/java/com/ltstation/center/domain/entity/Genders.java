package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table genders (origine SQL Server : Genders). */
@Getter
@Setter
@Entity
@Table(name = "genders")
public class Genders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gender_id")
    private Integer genderId;

    @Column(name = "gender_desc")
    private String genderDesc;

}
