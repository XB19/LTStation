package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_commo_manage (origine SQL Server : Supplier_CommoManage). */
@Getter
@Setter
@Entity
@Table(name = "supplier_commo_manage")
public class SupplierCommoManage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "commo_name")
    private String commoName;

    @Column(name = "commo_code")
    private String commoCode;

    @Column(name = "specificat")
    private String specificat;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "initial_value")
    private BigDecimal initialValue;

    @Column(name = "unit")
    private String unit;

    @Column(name = "create_user")
    private String createUser;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "modified_user")
    private String modifiedUser;

    @Column(name = "modified_time")
    private LocalDateTime modifiedTime;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "oil_supply_id")
    private Integer oilSupplyId;

    @Column(name = "oil_supply_name")
    private String oilSupplyName;

    @Column(name = "oil_unit")
    private String oilUnit;

    @Column(name = "platform_id")
    private Integer platformId;

}
