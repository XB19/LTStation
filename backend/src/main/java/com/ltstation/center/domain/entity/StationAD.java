package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table station_ad (origine SQL Server : StationAD). */
@Getter
@Setter
@Entity
@Table(name = "station_ad")
public class StationAD {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "start_time")
    private Integer startTime;

    @Column(name = "end_time")
    private Integer endTime;

    @Column(name = "content_id")
    private Integer contentId;

    @Column(name = "company_id")
    private Integer companyId;

}
