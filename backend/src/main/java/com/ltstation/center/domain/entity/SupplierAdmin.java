package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_admin (origine SQL Server : Supplier_Admin). */
@Getter
@Setter
@Entity
@Table(name = "supplier_admin")
public class SupplierAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "admin_name")
    private String adminName;

    @Column(name = "admin_psw")
    private String adminPsw;

    @Column(name = "menu_permis")
    private String menuPermis;

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "platform_id")
    private Integer platformId;

}
