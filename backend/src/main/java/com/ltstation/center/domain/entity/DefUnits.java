package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_units (origine SQL Server : DefUnits). */
@Getter
@Setter
@Entity
@Table(name = "def_units")
public class DefUnits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unit_id")
    private Integer unitId;

    @Column(name = "unit_desc")
    private String unitDesc;

}
