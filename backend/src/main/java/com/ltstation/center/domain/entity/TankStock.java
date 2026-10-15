package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table tank_stock (origine SQL Server : TankStock). */
@Getter
@Setter
@Entity
@Table(name = "tank_stock")
public class TankStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "tank_id")
    private Integer tankId;

    @Column(name = "tank_name")
    private String tankName;

    @Column(name = "stock_volume")
    private BigDecimal stockVolume;

    @Column(name = "final_stock")
    private BigDecimal finalStock;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "start_stock")
    private Integer startStock;

    @Column(name = "company_id")
    private Integer companyId;

}
