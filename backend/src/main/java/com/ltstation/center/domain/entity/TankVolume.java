package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table tank_volume (origine SQL Server : TankVolume). */
@Getter
@Setter
@Entity
@Table(name = "tank_volume")
public class TankVolume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "tank_volume_id")
    private Integer tankVolumeId;

    @Column(name = "tank_volume_name")
    private String tankVolumeName;

    @Column(name = "height")
    private BigDecimal height;

    @Column(name = "volume")
    private BigDecimal volume;

}
