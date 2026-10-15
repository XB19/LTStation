package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_role (origine SQL Server : Supplier_Role). */
@Getter
@Setter
@Entity
@Table(name = "supplier_role")
public class SupplierRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "role_code")
    private String roleCode;

    @Column(name = "menu_permis")
    private String menuPermis;

    @Column(name = "site_permis")
    private String sitePermis;

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

    @Column(name = "oil_supply_id")
    private Integer oilSupplyId;

    @Column(name = "oil_supply_name")
    private String oilSupplyName;

}
