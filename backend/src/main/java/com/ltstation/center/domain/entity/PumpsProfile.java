package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table pumps_profile (origine SQL Server : PumpsProfile). */
@Getter
@Setter
@Entity
@Table(name = "pumps_profile")
@IdClass(PumpsProfile.Pk.class)
public class PumpsProfile {

    @Id
    @Column(name = "station_id")
    private Integer stationId;

    @Id
    @Column(name = "pump_id")
    private Integer pumpId;

    @Column(name = "brand")
    private String brand;

    @Column(name = "model")
    private String model;

    @Column(name = "install_data")
    private LocalDateTime installData;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer stationId;
        private Integer pumpId;
    }
}
