package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table data_versions (origine SQL Server : DataVersions). */
@Getter
@Setter
@Entity
@Table(name = "data_versions")
public class DataVersions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_version_id")
    private Integer dataVersionId;

    @Column(name = "machine_cfg")
    private Integer machineCfg;

    @Column(name = "machine_cfg_time")
    private LocalDateTime machineCfgTime;

    @Column(name = "ppu")
    private Integer ppu;

    @Column(name = "ppu_time")
    private LocalDateTime ppuTime;

    @Column(name = "grade_spec")
    private Integer gradeSpec;

    @Column(name = "grade_spec_time")
    private LocalDateTime gradeSpecTime;

    @Column(name = "basic_black")
    private Integer basicBlack;

    @Column(name = "basic_black_time")
    private LocalDateTime basicBlackTime;

    @Column(name = "append_black")
    private Integer appendBlack;

    @Column(name = "append_black_time")
    private LocalDateTime appendBlackTime;

    @Column(name = "del_black")
    private Integer delBlack;

    @Column(name = "del_black_time")
    private LocalDateTime delBlackTime;

    @Column(name = "white")
    private Integer white;

    @Column(name = "white_time")
    private LocalDateTime whiteTime;

    @Column(name = "gray")
    private Integer gray;

    @Column(name = "gray_time")
    private LocalDateTime grayTime;

    @Column(name = "employee")
    private Integer employee;

    @Column(name = "employee_time")
    private LocalDateTime employeeTime;

    @Column(name = "coupon")
    private Integer coupon;

    @Column(name = "coupon_time")
    private LocalDateTime couponTime;

    @Column(name = "transport_infoes")
    private Integer transportInfoes;

    @Column(name = "transport_infoes_time")
    private LocalDateTime transportInfoesTime;

    @Column(name = "distribution_points")
    private Integer distributionPoints;

    @Column(name = "distribution_points_time")
    private LocalDateTime distributionPointsTime;

}
