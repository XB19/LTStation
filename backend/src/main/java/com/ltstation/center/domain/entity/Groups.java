package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table groups (origine SQL Server : Groups). */
@Getter
@Setter
@Entity
@Table(name = "groups")
public class Groups {

    @Id
    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "name")
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "code")
    private String code;

    @Column(name = "address")
    private String address;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "fax")
    private String fax;

    @Column(name = "post_code")
    private String postCode;

    @Column(name = "contact")
    private String contact;

    @Column(name = "certificate_no")
    private String certificateNo;

    @Column(name = "openbank")
    private String openbank;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "lown_flg")
    private String lownFlg;

    @Column(name = "memo")
    private String memo;

    @Column(name = "email")
    private String email;

    @Column(name = "company_id")
    private Integer companyId;

}
