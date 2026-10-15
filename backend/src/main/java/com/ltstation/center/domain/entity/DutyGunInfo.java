package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table duty_gun_info (origine SQL Server : DutyGunInfo). */
@Getter
@Setter
@Entity
@Table(name = "duty_gun_info")
public class DutyGunInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "gun_code")
    private Integer gunCode;

    @Column(name = "startpt")
    private Double startpt;

    @Column(name = "end_pt")
    private Double endPt;

    @Column(name = "vol")
    private Double vol;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "shift")
    private String shift;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "ptc")
    private Double ptc;

    @Column(name = "time")
    private LocalDateTime time;

    @Column(name = "memo")
    private String memo;

}
