package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gprs_nozzle (origine SQL Server : GPRSNozzle). */
@Getter
@Setter
@Entity
@Table(name = "gprs_nozzle")
public class GPRSNozzle {

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "iccid")
    private String iccid;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "oil_id")
    private Integer oilId;

    @Column(name = "totalliser")
    private BigDecimal totalliser;

    @Column(name = "calibration")
    private BigDecimal calibration;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "is_monitor")
    private Boolean isMonitor;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "is_connect")
    private Boolean isConnect;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "oil_name")
    private String oilName;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "is_lock")
    private Integer isLock;

    @Column(name = "tank_id")
    private Integer tankId;

    @Column(name = "is_run")
    private Boolean isRun;

}
