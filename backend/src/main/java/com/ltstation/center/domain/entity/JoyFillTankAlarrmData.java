package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_tank_alarrm_data (origine SQL Server : JoyFill_tankAlarrmData). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_tank_alarrm_data")
public class JoyFillTankAlarrmData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "company_uuid")
    private String companyUuid;

    @Column(name = "station_uuid")
    private String stationUuid;

    @Column(name = "state")
    private Integer state;

    @Column(name = "tank_no")
    private Integer tankNo;

    @Column(name = "note")
    private String note;

    @Column(name = "type")
    private String type;

    @Column(name = "request_time")
    private LocalDateTime requestTime;

    @Column(name = "request_status")
    private Integer requestStatus;

    @Column(name = "request_result")
    private String requestResult;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "tank_id")
    private Integer tankId;

    @Column(name = "tank_name")
    private String tankName;

}
