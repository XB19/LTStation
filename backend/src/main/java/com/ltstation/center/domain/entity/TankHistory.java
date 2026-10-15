package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table tank_history (origine SQL Server : TankHistory). */
@Getter
@Setter
@Entity
@Table(name = "tank_history")
public class TankHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "tank_id")
    private Integer tankId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "height")
    private BigDecimal height;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "temp")
    private BigDecimal temp;

    @Column(name = "water_height")
    private BigDecimal waterHeight;

    @Column(name = "water_volume")
    private BigDecimal waterVolume;

    @Column(name = "station_id")
    private Integer stationId;

}
