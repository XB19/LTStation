package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table platform_fee (origine SQL Server : PlatformFee). */
@Getter
@Setter
@Entity
@Table(name = "platform_fee")
public class PlatformFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "openid")
    private String openid;

    @Column(name = "month")
    private Integer month;

    @Column(name = "\"using\"")
    private Integer using;

}
