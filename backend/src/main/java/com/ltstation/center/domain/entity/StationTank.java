package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table station_tank (origine SQL Server : StationTank). */
@Getter
@Setter
@Entity
@Table(name = "station_tank")
public class StationTank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "tank_no")
    private Integer tankNo;

    @Column(name = "company_id")
    private Integer companyId;

}
