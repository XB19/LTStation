package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table box_pay_order (origine SQL Server : Box_Pay_Order). */
@Getter
@Setter
@Entity
@Table(name = "box_pay_order")
public class BoxPayOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "nozid")
    private Integer nozid;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "pay_way")
    private String payWay;

    @Column(name = "card_no")
    private String cardNo;

    @Column(name = "car_no")
    private String carNo;

    @Column(name = "card_balance")
    private BigDecimal cardBalance;

    @Column(name = "link_id")
    private Integer linkId;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "transaction_id")
    private Integer transactionId;

    @Column(name = "transaction_amount")
    private BigDecimal transactionAmount;

}
