package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table vehicle_colors (origine SQL Server : VehicleColors). */
@Getter
@Setter
@Entity
@Table(name = "vehicle_colors")
public class VehicleColors {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_color_id")
    private Integer vehicleColorId;

    @Column(name = "name")
    private String name;

    @Column(name = "name_en")
    private String nameEn;

}
