package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table tank_real_data (origine SQL Server : TankRealData). */
@Getter
@Setter
@Entity
@Table(name = "tank_real_data")
public class TankRealData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "tank_id")
    private Integer tankId;

    @Column(name = "tank_name")
    private String tankName;

    @Column(name = "grade_id")
    private Integer gradeId;

    @Column(name = "grade_name")
    private String gradeName;

    @Column(name = "tank_height")
    private BigDecimal tankHeight;

    @Column(name = "tank_volume")
    private BigDecimal tankVolume;

    @Column(name = "current_height")
    private BigDecimal currentHeight;

    @Column(name = "current_volume")
    private BigDecimal currentVolume;

    @Column(name = "current_water_height")
    private BigDecimal currentWaterHeight;

    @Column(name = "current_temp")
    private BigDecimal currentTemp;

    @Column(name = "update_time")
    private Long updateTime;

    @Column(name = "oil_volume_ratio")
    private BigDecimal oilVolumeRatio;

    @Column(name = "water_height_ratio")
    private BigDecimal waterHeightRatio;

    @Column(name = "company_id")
    private Integer companyId;

}
