package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table manufacturers (origine SQL Server : Manufacturers). */
@Getter
@Setter
@Entity
@Table(name = "manufacturers")
public class Manufacturers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manufacturer_id")
    private Integer manufacturerId;

    @Column(name = "name")
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "classification_id")
    private Integer classificationId;

    @Column(name = "unify_no")
    private String unifyNo;

    @Column(name = "head")
    private String head;

    @Column(name = "contact")
    private String contact;

    @Column(name = "business_man")
    private String businessMan;

    @Column(name = "phone1")
    private String phone1;

    @Column(name = "phone2")
    private String phone2;

    @Column(name = "phone_delivery")
    private String phoneDelivery;

    @Column(name = "fax1")
    private String fax1;

    @Column(name = "fax2")
    private String fax2;

    @Column(name = "address_registered")
    private String addressRegistered;

    @Column(name = "address")
    private String address;

    @Column(name = "address_delivery")
    private String addressDelivery;

    @Column(name = "postcode_registered")
    private String postcodeRegistered;

    @Column(name = "postcode")
    private String postcode;

    @Column(name = "postcode_delivery")
    private String postcodeDelivery;

    @Column(name = "bank")
    private String bank;

    @Column(name = "account_no")
    private String accountNo;

    @Column(name = "tax_no")
    private String taxNo;

    @Column(name = "discount")
    private Double discount;

    @Column(name = "credit_line")
    private Double creditLine;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "date_payment_monthly")
    private Integer datePaymentMonthly;

    @Column(name = "debt")
    private Double debt;

    @Column(name = "date_recent_trading")
    private LocalDateTime dateRecentTrading;

    @Column(name = "date_create")
    private LocalDateTime dateCreate;

    @Column(name = "memo")
    private String memo;

    @Column(name = "reserve_a")
    private String reserveA;

    @Column(name = "reserve_b")
    private String reserveB;

    @Column(name = "reserve_c")
    private String reserveC;

}
