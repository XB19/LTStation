package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table monitor_device (origine SQL Server : MonitorDevice). */
@Getter
@Setter
@Entity
@Table(name = "monitor_device")
public class MonitorDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "device_url")
    private String deviceUrl;

    @Column(name = "device_token")
    private String deviceToken;

}
