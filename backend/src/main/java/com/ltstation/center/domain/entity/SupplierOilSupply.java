package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_oil_supply (origine SQL Server : Supplier_OilSupply). */
@Getter
@Setter
@Entity
@Table(name = "supplier_oil_supply")
public class SupplierOilSupply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "platform_id")
    private Integer platformId;

    @Column(name = "oil_supply_name")
    private String oilSupplyName;

    @Column(name = "connect_personnel")
    private String connectPersonnel;

    @Column(name = "connect_telephone")
    private String connectTelephone;

    @Column(name = "address")
    private String address;

}
