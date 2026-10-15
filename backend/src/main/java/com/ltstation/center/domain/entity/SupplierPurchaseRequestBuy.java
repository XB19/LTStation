package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_purchase_request_buy (origine SQL Server : Supplier_PurchaseRequestBuy). */
@Getter
@Setter
@Entity
@Table(name = "supplier_purchase_request_buy")
public class SupplierPurchaseRequestBuy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "platform_id")
    private Integer platformId;

    @Column(name = "price_unit")
    private String priceUnit;

    @Column(name = "oil_unit")
    private String oilUnit;

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

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "oil_value")
    private BigDecimal oilValue;

    @Column(name = "request_state")
    private Integer requestState;

    @Column(name = "request_time")
    private LocalDateTime requestTime;

}
