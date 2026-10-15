package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_platform_manage (origine SQL Server : Supplier_PlatformManage). */
@Getter
@Setter
@Entity
@Table(name = "supplier_platform_manage")
public class SupplierPlatformManage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "platform_name")
    private String platformName;

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "limit_number")
    private Integer limitNumber;

    @Column(name = "price_unit")
    private String priceUnit;

    @Column(name = "oil_unit")
    private String oilUnit;

    @Column(name = "joy_fill_platform_ids")
    private String joyFillPlatformIds;

}
