package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gprs_account (origine SQL Server : GPRSAccount). */
@Getter
@Setter
@Entity
@Table(name = "gprs_account")
public class GPRSAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "password")
    private String password;

    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "belong_to_id")
    private Integer belongToId;

    @Column(name = "openid")
    private String openid;

    @Column(name = "type_id")
    private Integer typeId;

    @Column(name = "phone")
    private String phone;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "out_time")
    private LocalDateTime outTime;

    @Column(name = "head_url")
    private String headUrl;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "emp_id")
    private Integer empId;

    @Column(name = "auth")
    private String auth;

}
