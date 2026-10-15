package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table grade_specifics (origine SQL Server : GradeSpecifics). */
@Getter
@Setter
@Entity
@Table(name = "grade_specifics")
public class GradeSpecifics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "alias")
    private String alias;

    @Column(name = "app_number")
    private Integer appNumber;

    @Column(name = "company_id")
    private Integer companyId;

}
