package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table tank_status (origine SQL Server : TankStatus). */
@Getter
@Setter
@Entity
@Table(name = "tank_status")
@IdClass(TankStatus.Pk.class)
public class TankStatus {

    @Id
    @Column(name = "id")
    private Integer id;

    @Id
    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "oil_height")
    private Double oilHeight;

    @Column(name = "oil_vol")
    private Double oilVol;

    @Column(name = "oil_temp")
    private Double oilTemp;

    @Column(name = "sales")
    private Double sales;

    @Column(name = "water_height")
    private Double waterHeight;

    @Column(name = "water_vol")
    private Double waterVol;

    @Column(name = "oil_density")
    private Double oilDensity;

    @Column(name = "order_limit")
    private Double orderLimit;

    @Column(name = "water_upper")
    private Integer waterUpper;

    @Column(name = "oil_over_flow")
    private Integer oilOverFlow;

    @Column(name = "oil_upper")
    private Integer oilUpper;

    @Column(name = "oil_lower")
    private Integer oilLower;

    @Column(name = "low_temp")
    private Integer lowTemp;

    @Column(name = "in_tank")
    private Integer inTank;

    @Column(name = "in_out")
    private Integer inOut;

    @Column(name = "hinvalid")
    private Integer hinvalid;

    @Column(name = "probeout")
    private Integer probeout;

    @Column(name = "other")
    private Integer other;

    @Column(name = "time")
    private LocalDateTime time;

    @Column(name = "tank_height")
    private Double tankHeight;

    @Column(name = "tank_vol")
    private Double tankVol;

    @Column(name = "oil_code")
    private String oilCode;

    @Column(name = "update_time")
    private Long updateTime;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer id;
        private Integer stationId;
    }
}
