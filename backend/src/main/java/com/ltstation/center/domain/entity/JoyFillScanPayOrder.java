package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_scan_pay_order (origine SQL Server : JoyFill_scanPayOrder). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_scan_pay_order")
public class JoyFillScanPayOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "gun_number")
    private Integer gunNumber;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "payment_state")
    private Integer paymentState;

    @Column(name = "pay_time")
    private LocalDateTime payTime;

    @Column(name = "real_refuel_amount")
    private BigDecimal realRefuelAmount;

    @Column(name = "real_refuel_liters")
    private BigDecimal realRefuelLiters;

    @Column(name = "need_refund")
    private Integer needRefund;

    @Column(name = "refund_balance")
    private BigDecimal refundBalance;

    @Column(name = "refund_time")
    private LocalDateTime refundTime;

    @Column(name = "refund_status")
    private Integer refundStatus;

    @Column(name = "open_id")
    private String openId;

    @Column(name = "sub_mch_id")
    private String subMchId;

    @Column(name = "sub_appid")
    private String subAppid;

    @Column(name = "out_trade_no")
    private String outTradeNo;

    @Column(name = "prepay_id")
    private String prepayId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "out_refund_no")
    private String outRefundNo;

    @Column(name = "refund_id")
    private String refundId;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "finish_state")
    private Integer finishState;

}
