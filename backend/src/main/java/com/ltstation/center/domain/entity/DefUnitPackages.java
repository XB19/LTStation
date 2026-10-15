package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_unit_packages (origine SQL Server : DefUnitPackages). */
@Getter
@Setter
@Entity
@Table(name = "def_unit_packages")
public class DefUnitPackages {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unit_package_id")
    private Integer unitPackageId;

    @Column(name = "unit_package_desc")
    private String unitPackageDesc;

}
