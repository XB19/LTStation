package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table duty_brand_sales (origine SQL Server : DutyBrandSales). */
@Getter
@Setter
@Entity
@Table(name = "duty_brand_sales")
@IdClass(DutyBrandSales.Pk.class)
public class DutyBrandSales {

    @Id
    @Column(name = "shift")
    private String shift;

    @Id
    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Id
    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "ppu")
    private Double ppu;

    @Column(name = "density")
    private Double density;

    @Column(name = "employee_v")
    private Double employeeV;

    @Column(name = "employee_m")
    private Double employeeM;

    @Column(name = "user_v")
    private Double userV;

    @Column(name = "user_m")
    private Double userM;

    @Column(name = "credit_v")
    private Double creditV;

    @Column(name = "credit_m")
    private Double creditM;

    @Column(name = "test_v")
    private Double testV;

    @Column(name = "test_m")
    private Double testM;

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

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String shift;
        private Integer gradeSpecificId;
        private Integer stationId;
    }
}
