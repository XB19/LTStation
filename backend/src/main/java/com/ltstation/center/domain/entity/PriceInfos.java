package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table price_infos (origine SQL Server : PriceInfos). */
@Getter
@Setter
@Entity
@Table(name = "price_infos")
public class PriceInfos {

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "current_ppu")
    private Double currentPpu;

    @Column(name = "new_ppu")
    private Double newPpu;

    @Column(name = "current_density")
    private Double currentDensity;

    @Column(name = "newdensity")
    private Double newdensity;

    @Column(name = "validate_time")
    private LocalDateTime validateTime;

    @Column(name = "company_id")
    private Integer companyId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

}
