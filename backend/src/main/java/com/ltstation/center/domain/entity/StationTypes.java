package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table station_types (origine SQL Server : StationTypes). */
@Getter
@Setter
@Entity
@Table(name = "station_types")
public class StationTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_type_id")
    private Integer stationTypeId;

    @Column(name = "station_type_desc")
    private String stationTypeDesc;

}
