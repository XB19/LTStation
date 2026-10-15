package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table company_managements (origine SQL Server : CompanyManagements). */
@Getter
@Setter
@Entity
@Table(name = "company_managements")
public class CompanyManagements {

    @Id
    @Column(name = "company_management_id")
    private Integer companyManagementId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "pob")
    private String pob;

    @Column(name = "post_code")
    private String postCode;

    @Column(name = "cr")
    private String cr;

    @Column(name = "phone")
    private String phone;

    @Column(name = "fax")
    private String fax;

    @Column(name = "website")
    private String website;

    @Column(name = "email")
    private String email;

}
