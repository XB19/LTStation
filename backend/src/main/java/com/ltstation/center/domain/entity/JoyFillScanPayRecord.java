package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_scan_pay_record (origine SQL Server : JoyFill_scanPayRecord). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_scan_pay_record")
public class JoyFillScanPayRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "out_trade_no")
    private String outTradeNo;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "real_refuel_amount")
    private BigDecimal realRefuelAmount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "refund_status")
    private Integer refundStatus;

    @Column(name = "payment_state")
    private Integer paymentState;

}
