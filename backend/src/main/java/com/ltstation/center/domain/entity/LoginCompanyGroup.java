package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table login_company_group (origine SQL Server : LoginCompanyGroup). */
@Getter
@Setter
@Entity
@Table(name = "login_company_group")
public class LoginCompanyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "creatre_time")
    private LocalDateTime creatreTime;

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "root_id")
    private Integer rootId;

    @Column(name = "valid_time")
    private Long validTime;

    @Column(name = "valid_secret_key")
    private String validSecretKey;

    @Column(name = "valid_count")
    private Integer validCount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "time_zone")
    private Integer timeZone;

}
