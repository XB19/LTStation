package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table tank_alarm (origine SQL Server : TankAlarm). */
@Getter
@Setter
@Entity
@Table(name = "tank_alarm")
public class TankAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "tank_code")
    private Integer tankCode;

    @Column(name = "alarm_type_id")
    private Integer alarmTypeId;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "release_time")
    private LocalDateTime releaseTime;

    @Column(name = "upload")
    private Integer upload;

}
