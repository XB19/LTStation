package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table daily_gun_reports (origine SQL Server : DailyGunReports). */
@Getter
@Setter
@Entity
@Table(name = "daily_gun_reports")
public class DailyGunReports {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "shift")
    private String shift;

    @Column(name = "gun_code")
    private Integer gunCode;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "start_pt")
    private Double startPt;

    @Column(name = "end_pt")
    private Double endPt;

    @Column(name = "ptc")
    private Double ptc;

    @Column(name = "volume")
    private Double volume;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "account_time")
    private LocalDateTime accountTime;

    @Column(name = "upload")
    private Integer upload;

    @Column(name = "memo")
    private String memo;

}
