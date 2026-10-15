package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table cylinder (origine SQL Server : Cylinder). */
@Getter
@Setter
@Entity
@Table(name = "cylinder")
public class Cylinder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "car_user_id")
    private Integer carUserId;

    @Column(name = "vehicle_id")
    private String vehicleId;

    @Column(name = "cylinder_type_id")
    private String cylinderTypeId;

    @Column(name = "cylinder_type")
    private String cylinderType;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "medium_id")
    private String mediumId;

    @Column(name = "medium")
    private String medium;

    @Column(name = "work_pressure")
    private BigDecimal workPressure;

    @Column(name = "cubage")
    private BigDecimal cubage;

    @Column(name = "wall_thickness")
    private BigDecimal wallThickness;

    @Column(name = "manufacture_id")
    private String manufactureId;

    @Column(name = "manufacture_name")
    private String manufactureName;

    @Column(name = "production_date")
    private String productionDate;

    @Column(name = "product_no")
    private String productNo;

    @Column(name = "owned_no")
    private String ownedNo;

    @Column(name = "mj_date")
    private String mjDate;

    @Column(name = "xj_date")
    private String xjDate;

    @Column(name = "bf_date")
    private String bfDate;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "update_time")
    private Long updateTime;

    @Column(name = "cylinder_id")
    private String cylinderId;

}
