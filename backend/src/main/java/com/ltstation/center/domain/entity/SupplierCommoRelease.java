package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_commo_release (origine SQL Server : Supplier_CommoRelease). */
@Getter
@Setter
@Entity
@Table(name = "supplier_commo_release")
public class SupplierCommoRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "commo_id")
    private Integer commoId;

    @Column(name = "commo_name")
    private String commoName;

    @Column(name = "commo_code")
    private String commoCode;

    @Column(name = "specificat")
    private String specificat;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "release_state")
    private Integer releaseState;

    @Column(name = "release_time")
    private LocalDateTime releaseTime;

    @Column(name = "oil_supply_id")
    private Integer oilSupplyId;

    @Column(name = "oil_supply_name")
    private String oilSupplyName;

    @Column(name = "platform_id")
    private Integer platformId;

    @Column(name = "joy_fill_station_i_ds")
    private String joyFillStationIDs;

    @Column(name = "platform_name")
    private String platformName;

}
