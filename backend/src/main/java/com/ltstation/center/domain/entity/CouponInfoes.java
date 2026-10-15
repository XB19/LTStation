package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table coupon_infoes (origine SQL Server : CouponInfoes). */
@Getter
@Setter
@Entity
@Table(name = "coupon_infoes")
public class CouponInfoes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_info_id")
    private Integer couponInfoId;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "bar")
    private String bar;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "issue_time")
    private LocalDateTime issueTime;

    @Column(name = "recovery_time")
    private LocalDateTime recoveryTime;

    @Column(name = "memo1")
    private String memo1;

    @Column(name = "memo2")
    private String memo2;

}
