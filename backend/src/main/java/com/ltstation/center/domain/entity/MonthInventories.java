package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table month_inventories (origine SQL Server : MonthInventories). */
@Getter
@Setter
@Entity
@Table(name = "month_inventories")
public class MonthInventories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "month")
    private String month;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

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

    @Column(name = "upload")
    private Integer upload;

    @Column(name = "memo")
    private String memo;

}
