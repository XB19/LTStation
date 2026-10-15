package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table daily_inventories (origine SQL Server : DailyInventories). */
@Getter
@Setter
@Entity
@Table(name = "daily_inventories")
public class DailyInventories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "shift")
    private String shift;

    @Column(name = "tank_code")
    private Integer tankCode;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "density")
    private Double density;

    @Column(name = "begin_stock")
    private Double beginStock;

    @Column(name = "instock_l")
    private Double instockL;

    @Column(name = "instoc_kg")
    private Double instocKg;

    @Column(name = "outstock_l")
    private Double outstockL;

    @Column(name = "outstoc_kg")
    private Double outstocKg;

    @Column(name = "sales_l")
    private Double salesL;

    @Column(name = "sales_kg")
    private Double salesKg;

    @Column(name = "end_stock")
    private Double endStock;

    @Column(name = "book_stock")
    private Double bookStock;

    @Column(name = "gap")
    private Double gap;

    @Column(name = "account_time")
    private LocalDateTime accountTime;

    @Column(name = "upload")
    private Integer upload;

    @Column(name = "memo")
    private String memo;

}
