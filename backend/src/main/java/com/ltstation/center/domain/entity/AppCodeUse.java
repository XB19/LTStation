package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table app_code_use (origine SQL Server : AppCodeUse). */
@Getter
@Setter
@Entity
@Table(name = "app_code_use")
public class AppCodeUse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "app_code")
    private String appCode;

    @Column(name = "app_code_short")
    private String appCodeShort;

    @Column(name = "asn")
    private String asn;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "balance")
    private BigDecimal balance;

    @Column(name = "limit_oil")
    private String limitOil;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "is_using")
    private Integer isUsing;

    @Column(name = "app_type")
    private Integer appType;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "using_amount")
    private BigDecimal usingAmount;

}
