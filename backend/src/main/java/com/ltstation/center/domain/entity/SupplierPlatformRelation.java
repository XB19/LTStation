package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_platform_relation (origine SQL Server : Supplier_PlatformRelation). */
@Getter
@Setter
@Entity
@Table(name = "supplier_platform_relation")
public class SupplierPlatformRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "platform_id")
    private Integer platformId;

    @Column(name = "joy_fill_platform_id")
    private Integer joyFillPlatformId;

}
