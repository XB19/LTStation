package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table tank_alarm_history (origine SQL Server : TankAlarmHistory). */
@Getter
@Setter
@Entity
@Table(name = "tank_alarm_history")
public class TankAlarmHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "tank_id")
    private Integer tankId;

    @Column(name = "tank_name")
    private String tankName;

    @Column(name = "alarm_type_id")
    private Integer alarmTypeId;

    @Column(name = "update_time")
    private Long updateTime;

    @Column(name = "start_time")
    private Long startTime;

    @Column(name = "end_time")
    private Long endTime;

    @Column(name = "deal")
    private Boolean deal;

    @Column(name = "company_id")
    private Integer companyId;

}
