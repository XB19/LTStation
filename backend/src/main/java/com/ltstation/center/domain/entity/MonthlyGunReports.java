package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table monthly_gun_reports (origine SQL Server : MonthlyGunReports). */
@Getter
@Setter
@Entity
@Table(name = "monthly_gun_reports")
public class MonthlyGunReports {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "month")
    private String month;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "gun_code")
    private Integer gunCode;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "start_pt")
    private Double startPt;

    @Column(name = "end_pt")
    private Double endPt;

    @Column(name = "total_sales")
    private Double totalSales;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "gap")
    private Double gap;

    @Column(name = "upload")
    private Integer upload;

    @Column(name = "memo")
    private String memo;

}
