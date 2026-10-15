package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table supplier_purchase_order (origine SQL Server : Supplier_PurchaseOrder). */
@Getter
@Setter
@Entity
@Table(name = "supplier_purchase_order")
public class SupplierPurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "platform_id")
    private Integer platformId;

    @Column(name = "platform_name")
    private String platformName;

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

    @Column(name = "seller_name")
    private String sellerName;

    @Column(name = "seller_telephone")
    private String sellerTelephone;

    @Column(name = "ship_time")
    private LocalDateTime shipTime;

    @Column(name = "deliver_name")
    private String deliverName;

    @Column(name = "deliver_telephone")
    private String deliverTelephone;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "login_name")
    private String loginName;

    @Column(name = "employees_name")
    private String employeesName;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "oil_value")
    private BigDecimal oilValue;

    @Column(name = "storage_time")
    private LocalDateTime storageTime;

    @Column(name = "purchase_state")
    private Integer purchaseState;

    @Column(name = "purchase_time")
    private LocalDateTime purchaseTime;

    @Column(name = "real_oil_value")
    private BigDecimal realOilValue;

    @Column(name = "real_storage_time")
    private LocalDateTime realStorageTime;

    @Column(name = "purchase_amount")
    private BigDecimal purchaseAmount;

    @Column(name = "seller_employee_name")
    private String sellerEmployeeName;

    @Column(name = "purchase_file_ids")
    private String purchaseFileIds;

}
