package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_commo_stock (origine SQL Server : Supplier_CommoStock). */
@Getter
@Setter
@Entity
@Table(name = "supplier_commo_stock")
public class SupplierCommoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "oil_supply_id")
    private Integer oilSupplyId;

    @Column(name = "oil_supply_name")
    private String oilSupplyName;

    @Column(name = "commo_id")
    private Integer commoId;

    @Column(name = "commo_name")
    private String commoName;

    @Column(name = "commo_code")
    private String commoCode;

    @Column(name = "specificat")
    private String specificat;

    @Column(name = "liquidhigh")
    private BigDecimal liquidhigh;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

}
