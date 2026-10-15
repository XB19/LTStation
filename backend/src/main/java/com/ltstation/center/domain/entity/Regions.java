package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table regions (origine SQL Server : Regions). */
@Getter
@Setter
@Entity
@Table(name = "regions")
public class Regions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_id")
    private Integer regionId;

    @Column(name = "region_desc")
    private String regionDesc;

    @Column(name = "code")
    private String code;

    @Column(name = "code_inner")
    private String codeInner;

    @Column(name = "company_id")
    private Integer companyId;

}
