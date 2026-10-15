package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table stations (origine SQL Server : Stations). */
@Getter
@Setter
@Entity
@Table(name = "stations")
public class Stations {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "station_type_id")
    private Integer stationTypeId;

    @Column(name = "city_id")
    private Integer cityId;

    @Column(name = "postal")
    private String postal;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "fax")
    private String fax;

    @Column(name = "manager")
    private String manager;

    @Column(name = "memo")
    private String memo;

    @Column(name = "contacts1")
    private String contacts1;

    @Column(name = "contacts2")
    private String contacts2;

    @Column(name = "contacts3")
    private String contacts3;

    @Column(name = "phone1")
    private String phone1;

    @Column(name = "phone2")
    private String phone2;

    @Column(name = "phone3")
    private String phone3;

    @Column(name = "email1")
    private String email1;

    @Column(name = "email2")
    private String email2;

    @Column(name = "email3")
    private String email3;

    @Column(name = "transport_info_id")
    private Integer transportInfoId;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "is_active")
    private Integer isActive;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "station_price")
    private BigDecimal stationPrice;

    @Column(name = "two_year")
    private Integer twoYear;

    @Column(name = "three_year")
    private Integer threeYear;

    @Column(name = "authorize")
    private Integer authorize;

    @Column(name = "box_version")
    private String boxVersion;

    @Column(name = "station_mac")
    private String stationMac;

}
