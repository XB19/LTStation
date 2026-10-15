package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table air_tank (origine SQL Server : AirTank). */
@Getter
@Setter
@Entity
@Table(name = "air_tank")
public class AirTank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "grade_id")
    private Integer gradeId;

    @Column(name = "over_height")
    private BigDecimal overHeight;

    @Column(name = "lower_height")
    private BigDecimal lowerHeight;

    @Column(name = "over_temp")
    private BigDecimal overTemp;

    @Column(name = "lower_temp")
    private BigDecimal lowerTemp;

    @Column(name = "over_water")
    private BigDecimal overWater;

    @Column(name = "lower_water")
    private BigDecimal lowerWater;

    @Column(name = "current_height")
    private BigDecimal currentHeight;

    @Column(name = "current_oil")
    private BigDecimal currentOil;

    @Column(name = "current_temp")
    private BigDecimal currentTemp;

    @Column(name = "is_run")
    private Boolean isRun;

    @Column(name = "total_height")
    private BigDecimal totalHeight;

    @Column(name = "total_volume")
    private BigDecimal totalVolume;

    @Column(name = "tank_name")
    private String tankName;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "electrostatic_grounding")
    private Integer electrostaticGrounding;

    @Column(name = "tankleakage")
    private Integer tankleakage;

    @Column(name = "combustible_gas")
    private Integer combustibleGas;

    @Column(name = "tankhightemperature")
    private Integer tankhightemperature;

    @Column(name = "tank_level")
    private Integer tankLevel;

    @Column(name = "tank_volume_id")
    private Integer tankVolumeId;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "tank_set_id")
    private Integer tankSetId;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "current_water")
    private BigDecimal currentWater;

}
