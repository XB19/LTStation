package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table box_set (origine SQL Server : BoxSet). */
@Getter
@Setter
@Entity
@Table(name = "box_set")
public class BoxSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "create_time")
    private Long createTime;

}
