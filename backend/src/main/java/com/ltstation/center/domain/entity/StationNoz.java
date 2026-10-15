package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table station_noz (origine SQL Server : StationNoz). */
@Getter
@Setter
@Entity
@Table(name = "station_noz")
public class StationNoz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "noz_id")
    private Integer nozId;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "grade_id")
    private Integer gradeId;

    @Column(name = "grade_name")
    private String gradeName;

    @Column(name = "tank_id")
    private Integer tankId;

    @Column(name = "tank_name")
    private String tankName;

    @Column(name = "price")
    private BigDecimal price;

}
