package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table cities (origine SQL Server : Cities). */
@Getter
@Setter
@Entity
@Table(name = "cities")
public class Cities {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "city_id")
    private Integer cityId;

    @Column(name = "city_desc")
    private String cityDesc;

    @Column(name = "code")
    private String code;

    @Column(name = "region_id")
    private Integer regionId;

    @Column(name = "code_inner")
    private String codeInner;

    @Column(name = "company_id")
    private Integer companyId;

}
