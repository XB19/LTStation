package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table cmd_list (origine SQL Server : CmdList). */
@Getter
@Setter
@Entity
@Table(name = "cmd_list")
public class CmdList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "cmd_type")
    private String cmdType;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "noz_id")
    private Integer nozId;

    @Column(name = "cmd_data")
    private String cmdData;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "deal")
    private Integer deal;

    @Column(name = "cmd_plat_type")
    private Integer cmdPlatType;

    @Column(name = "dowload")
    private Boolean dowload;

    @Column(name = "station_id")
    private Integer stationId;

}
