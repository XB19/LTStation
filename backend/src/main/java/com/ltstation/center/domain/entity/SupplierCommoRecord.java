package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_commo_record (origine SQL Server : Supplier_CommoRecord). */
@Getter
@Setter
@Entity
@Table(name = "supplier_commo_record")
public class SupplierCommoRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "commo_id")
    private Integer commoId;

    @Column(name = "commo_name")
    private String commoName;

    @Column(name = "specificat")
    private String specificat;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "release_time")
    private LocalDateTime releaseTime;

    @Column(name = "platform_id")
    private Integer platformId;

}
