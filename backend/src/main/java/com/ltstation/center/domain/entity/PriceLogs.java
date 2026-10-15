package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table price_logs (origine SQL Server : PriceLogs). */
@Getter
@Setter
@Entity
@Table(name = "price_logs")
public class PriceLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "ppu")
    private Double ppu;

    @Column(name = "density")
    private Double density;

    @Column(name = "scope")
    private String scope;

    @Column(name = "validate_time")
    private LocalDateTime validateTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

}
