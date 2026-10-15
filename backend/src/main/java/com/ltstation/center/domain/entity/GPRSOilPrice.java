package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gprs_oil_price (origine SQL Server : GPRSOilPrice). */
@Getter
@Setter
@Entity
@Table(name = "gprs_oil_price")
public class GPRSOilPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "oil_code")
    private String oilCode;

    @Column(name = "oil_name")
    private String oilName;

    @Column(name = "oil_price_pre")
    private BigDecimal oilPricePre;

    @Column(name = "oil_price_cur")
    private BigDecimal oilPriceCur;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "effective_time")
    private LocalDateTime effectiveTime;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "is_run")
    private Boolean isRun;

}
