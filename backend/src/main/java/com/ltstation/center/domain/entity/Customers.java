package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table customers (origine SQL Server : Customers). */
@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customers {

    @Id
    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "certificate_type_id")
    private Integer certificateTypeId;

    @Column(name = "certificate_no")
    private String certificateNo;

    @Column(name = "address")
    private String address;

    @Column(name = "post_code")
    private String postCode;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "email")
    private String email;

    @Column(name = "vehicle_no")
    private String vehicleNo;

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "issue_status")
    private Boolean issueStatus;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "company")
    private String company;

    @Column(name = "birthday")
    private LocalDateTime birthday;

    @Column(name = "promo1")
    private String promo1;

    @Column(name = "promo2")
    private String promo2;

    @Column(name = "vehicle_type_id")
    private Integer vehicleTypeId;

    @Column(name = "vehicle_color_id")
    private Integer vehicleColorId;

    @Column(name = "app_password")
    private String appPassword;

    @Column(name = "app_telephone")
    private String appTelephone;

    @Column(name = "opend_id")
    private String opendId;

    @Column(name = "emp_phone")
    private String empPhone;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "np_value")
    private String npValue;

}
