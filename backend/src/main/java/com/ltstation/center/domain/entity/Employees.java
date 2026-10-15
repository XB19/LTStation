package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table employees (origine SQL Server : Employees). */
@Getter
@Setter
@Entity
@Table(name = "employees")
public class Employees {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "employee_no")
    private String employeeNo;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "certificate_type_id")
    private Integer certificateTypeId;

    @Column(name = "certify_key")
    private String certifyKey;

    @Column(name = "gender_id")
    private Integer genderId;

    @Column(name = "birthday")
    private LocalDateTime birthday;

    @Column(name = "address")
    private String address;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "post_code")
    private String postCode;

    @Column(name = "memo")
    private String memo;

    @Column(name = "code_inner")
    private Integer codeInner;

    @Column(name = "password")
    private String password;

    @Column(name = "score")
    private BigDecimal score;

    @Column(name = "is_using")
    private Boolean isUsing;

    @Column(name = "center_login")
    private Boolean centerLogin;

    @Column(name = "open_id")
    private String openId;

    @Column(name = "auth_id")
    private String authId;

    @Column(name = "gzh_auth")
    private String gzhAuth;

    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "pc_menu_auth")
    private String pcMenuAuth;

}
