package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table device_company_list (origine SQL Server : Device_Company_List). */
@Getter
@Setter
@Entity
@Table(name = "device_company_list")
public class DeviceCompanyList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "is_using")
    private Boolean isUsing;

}
