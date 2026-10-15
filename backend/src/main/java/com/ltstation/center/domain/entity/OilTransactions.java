package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table oil_transactions (origine SQL Server : OilTransactions). */
@Getter
@Setter
@Entity
@Table(name = "oil_transactions")
public class OilTransactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "t_type")
    private Integer tType;

    @Column(name = "true_time")
    private LocalDateTime trueTime;

    @Column(name = "asn")
    private String asn;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "volume")
    private Double volume;

    @Column(name = "bal")
    private Double bal;

    @Column(name = "amn")
    private Double amn;

    @Column(name = "ppu")
    private Double ppu;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "volume_total")
    private Double volumeTotal;

    @Column(name = "car_number")
    private String carNumber;

    @Column(name = "mileage")
    private Double mileage;

    @Column(name = "posttc")
    private String posttc;

    @Column(name = "ctc")
    private String ctc;

    @Column(name = "tac")
    private String tac;

    @Column(name = "gmac")
    private String gmac;

    @Column(name = "gtac")
    private String gtac;

    @Column(name = "psamtid")
    private String psamtid;

    @Column(name = "psamttc")
    private String psamttc;

    @Column(name = "upload")
    private String upload;

    @Column(name = "flg")
    private String flg;

    @Column(name = "discount")
    private Double discount;

    @Column(name = "preferential_price")
    private Double preferentialPrice;

    @Column(name = "coupon")
    private String coupon;

    @Column(name = "truely_pay")
    private Double truelyPay;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "psamasn")
    private String psamasn;

    @Column(name = "coupon_value")
    private Double couponValue;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "error_flg_id")
    private Integer errorFlgId;

    @Column(name = "bank_sid")
    private String bankSid;

    @Column(name = "tax_deal")
    private Boolean taxDeal;

    @Column(name = "pay_ment_deal_id")
    private Integer payMentDealId;

    @Column(name = "tank_id")
    private Integer tankId;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "amount_total")
    private BigDecimal amountTotal;

    @Column(name = "upload_third")
    private Boolean uploadThird;

    @Column(name = "order_id")
    private String orderId;

}
