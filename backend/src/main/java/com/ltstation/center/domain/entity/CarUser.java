package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table car_user (origine SQL Server : CarUser). */
@Getter
@Setter
@Entity
@Table(name = "car_user")
public class CarUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "car_group_id")
    private Integer carGroupId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "plate_number")
    private String plateNumber;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "construction_company")
    private Long constructionCompany;

    @Column(name = "supervision_org")
    private Long supervisionOrg;

    @Column(name = "start_date")
    private Long startDate;

    @Column(name = "security_admin")
    private String securityAdmin;

    @Column(name = "mobile_phone")
    private String mobilePhone;

    @Column(name = "applicant_user_id")
    private String applicantUserId;

    @Column(name = "review_passed")
    private Integer reviewPassed;

    @Column(name = "applicant_user")
    private String applicantUser;

    @Column(name = "balance")
    private BigDecimal balance;

    @Column(name = "update_time")
    private Long updateTime;

    @Column(name = "asn")
    private String asn;

    @Column(name = "customer_account_id")
    private Integer customerAccountId;

    @Column(name = "grade_id")
    private Integer gradeId;

}
