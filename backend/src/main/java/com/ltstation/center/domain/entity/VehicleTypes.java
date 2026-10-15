package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table vehicle_types (origine SQL Server : VehicleTypes). */
@Getter
@Setter
@Entity
@Table(name = "vehicle_types")
public class VehicleTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_type_id")
    private Integer vehicleTypeId;

    @Column(name = "name")
    private String name;

    @Column(name = "name_en")
    private String nameEn;

}
