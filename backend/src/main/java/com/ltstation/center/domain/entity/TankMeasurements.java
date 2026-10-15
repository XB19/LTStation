package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table tank_measurements (origine SQL Server : TankMeasurements). */
@Getter
@Setter
@Entity
@Table(name = "tank_measurements")
public class TankMeasurements {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "time")
    private LocalDateTime time;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "rec_seq")
    private Integer recSeq;

    @Column(name = "tank_code")
    private Integer tankCode;

    @Column(name = "shift")
    private String shift;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "water_height")
    private Double waterHeight;

    @Column(name = "water_volume")
    private Double waterVolume;

    @Column(name = "oil_height")
    private Double oilHeight;

    @Column(name = "oil_volume")
    private Double oilVolume;

    @Column(name = "oil_temp")
    private Double oilTemp;

    @Column(name = "samp_oil_temp")
    private Double sampOilTemp;

    @Column(name = "oil_density")
    private Double oilDensity;

    @Column(name = "samp_oil_density")
    private Double sampOilDensity;

    @Column(name = "upload")
    private Integer upload;

    @Column(name = "memo")
    private String memo;

    @Column(name = "employee_id")
    private Integer employeeId;

}
