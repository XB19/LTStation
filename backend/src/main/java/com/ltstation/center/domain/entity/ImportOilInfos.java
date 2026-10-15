package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table import_oil_infos (origine SQL Server : ImportOilInfos). */
@Getter
@Setter
@Entity
@Table(name = "import_oil_infos")
public class ImportOilInfos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "rec_seq")
    private Integer recSeq;

    @Column(name = "tank_code")
    private Integer tankCode;

    @Column(name = "grade_specific_id")
    private Integer gradeSpecificId;

    @Column(name = "shift")
    private String shift;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "trunk_id")
    private String trunkId;

    @Column(name = "pre_height")
    private Double preHeight;

    @Column(name = "pre_volume")
    private Double preVolume;

    @Column(name = "pre_temp")
    private Double preTemp;

    @Column(name = "after_height")
    private Double afterHeight;

    @Column(name = "after_volume")
    private Double afterVolume;

    @Column(name = "after_temp")
    private Double afterTemp;

    @Column(name = "sales")
    private Double sales;

    @Column(name = "real_import_volume")
    private Double realImportVolume;

    @Column(name = "imp_gap")
    private Double impGap;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @Column(name = "ticket_sn")
    private String ticketSn;

    @Column(name = "ticket_volume")
    private Double ticketVolume;

    @Column(name = "ticket_quantity")
    private String ticketQuantity;

    @Column(name = "ticket_density")
    private Double ticketDensity;

    @Column(name = "driver")
    private String driver;

    @Column(name = "upload")
    private Integer upload;

    @Column(name = "memo")
    private String memo;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "supplier")
    private String supplier;

}
