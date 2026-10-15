package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table cylinder_types (origine SQL Server : CylinderTypes). */
@Getter
@Setter
@Entity
@Table(name = "cylinder_types")
public class CylinderTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cylinder_type_id")
    private Integer cylinderTypeId;

    @Column(name = "name")
    private String name;

    @Column(name = "name_en")
    private String nameEn;

}
