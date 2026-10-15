package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gprs_transaction (origine SQL Server : GPRSTransaction). */
@Getter
@Setter
@Entity
@Table(name = "gprs_transaction")
public class GPRSTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "iccid")
    private String iccid;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "shift")
    private String shift;

    @Column(name = "oil_name")
    private String oilName;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "oil_id")
    private Integer oilId;

    @Column(name = "volume_total")
    private BigDecimal volumeTotal;

    @Column(name = "id_card")
    private String idCard;

    @Column(name = "volume_high")
    private BigDecimal volumeHigh;

    @Column(name = "user_id")
    private Integer userId;

}
