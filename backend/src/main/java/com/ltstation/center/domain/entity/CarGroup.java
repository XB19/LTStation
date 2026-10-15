package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table car_group (origine SQL Server : CarGroup). */
@Getter
@Setter
@Entity
@Table(name = "car_group")
public class CarGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "car_group_name")
    private String carGroupName;

    @Column(name = "car_group_address")
    private String carGroupAddress;

    @Column(name = "car_group_credit_code")
    private String carGroupCreditCode;

    @Column(name = "phone")
    private String phone;

    @Column(name = "group_code")
    private String groupCode;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "balance")
    private BigDecimal balance;

    @Column(name = "update_time")
    private Long updateTime;

    @Column(name = "company_id")
    private Integer companyId;

}
