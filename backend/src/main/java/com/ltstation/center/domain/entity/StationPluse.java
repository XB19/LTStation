package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table station_pluse (origine SQL Server : StationPluse). */
@Getter
@Setter
@Entity
@Table(name = "station_pluse")
public class StationPluse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "pluse")
    private Integer pluse;

    @Column(name = "dowload")
    private Boolean dowload;

    @Column(name = "createtime")
    private LocalDateTime createtime;

}
