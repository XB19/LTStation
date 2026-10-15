package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gun_data_error (origine SQL Server : GunDataError). */
@Getter
@Setter
@Entity
@Table(name = "gun_data_error")
public class GunDataError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "shift")
    private String shift;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "dt")
    private LocalDateTime dt;

    @Column(name = "volume_total")
    private Double volumeTotal;

    @Column(name = "old_volume_total")
    private Double oldVolumeTotal;

    @Column(name = "memo")
    private String memo;

    @Column(name = "upload")
    private Integer upload;

}
