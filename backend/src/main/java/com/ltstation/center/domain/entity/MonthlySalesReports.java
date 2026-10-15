package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table monthly_sales_reports (origine SQL Server : MonthlySalesReports). */
@Getter
@Setter
@Entity
@Table(name = "monthly_sales_reports")
public class MonthlySalesReports {

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

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "ppu")
    private Double ppu;

    @Column(name = "density")
    private Double density;

    @Column(name = "cash_v")
    private Double cashV;

    @Column(name = "cash_m")
    private Double cashM;

    @Column(name = "deposit_v")
    private Double depositV;

    @Column(name = "deposit_m")
    private Double depositM;

    @Column(name = "credit_v")
    private Double creditV;

    @Column(name = "credit_m")
    private Double creditM;

    @Column(name = "maint_v")
    private Double maintV;

    @Column(name = "maint_m")
    private Double maintM;

    @Column(name = "verify_v")
    private Double verifyV;

    @Column(name = "verify_m")
    private Double verifyM;

    @Column(name = "other_v")
    private Double otherV;

    @Column(name = "other_m")
    private Double otherM;

    @Column(name = "rcv")
    private Double rcv;

    @Column(name = "rcm")
    private Double rcm;

    @Column(name = "main_po_v")
    private Double mainPoV;

    @Column(name = "main_po_m")
    private Double mainPoM;

    @Column(name = "subpo_v")
    private Double subpoV;

    @Column(name = "subpo_m")
    private Double subpoM;

    @Column(name = "credit_coupons_v")
    private Double creditCouponsV;

    @Column(name = "credit_coupons_m")
    private Double creditCouponsM;

    @Column(name = "cash_coupons_v")
    private Double cashCouponsV;

    @Column(name = "cash_coupons_m")
    private Double cashCouponsM;

    @Column(name = "bank_v")
    private Double bankV;

    @Column(name = "bank_m")
    private Double bankM;

    @Column(name = "upload")
    private Integer upload;

    @Column(name = "memo")
    private String memo;

}
