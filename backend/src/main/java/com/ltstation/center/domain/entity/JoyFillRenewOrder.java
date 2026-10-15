package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_renew_order (origine SQL Server : JoyFill_renewOrder). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_renew_order")
public class JoyFillRenewOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "gun_number")
    private Integer gunNumber;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "login_name")
    private String loginName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "payment_state")
    private Integer paymentState;

    @Column(name = "pay_time")
    private LocalDateTime payTime;

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

    @Column(name = "expire_date_before")
    private LocalDateTime expireDateBefore;

    @Column(name = "expire_date_after")
    private LocalDateTime expireDateAfter;

    @Column(name = "recharge_number")
    private Integer rechargeNumber;

    @Column(name = "device_no")
    private String deviceNo;

}
