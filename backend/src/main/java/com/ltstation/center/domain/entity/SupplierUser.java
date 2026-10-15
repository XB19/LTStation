package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_user (origine SQL Server : Supplier_User). */
@Getter
@Setter
@Entity
@Table(name = "supplier_user")
public class SupplierUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_psw")
    private String userPsw;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "attri_role_id")
    private Integer attriRoleId;

    @Column(name = "attri_role_name")
    private String attriRoleName;

    @Column(name = "create_user")
    private String createUser;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "modified_user")
    private String modifiedUser;

    @Column(name = "modified_time")
    private LocalDateTime modifiedTime;

    @Column(name = "platform_id")
    private Integer platformId;

    @Column(name = "oil_supply_name")
    private String oilSupplyName;

    @Column(name = "oil_supply_id")
    private Integer oilSupplyId;

    @Column(name = "employee_name")
    private String employeeName;

}
