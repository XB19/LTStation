package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_unit_weights (origine SQL Server : DefUnitWeights). */
@Getter
@Setter
@Entity
@Table(name = "def_unit_weights")
public class DefUnitWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unit_weight_id")
    private Integer unitWeightId;

    @Column(name = "unit_weight_desc")
    private String unitWeightDesc;

}
