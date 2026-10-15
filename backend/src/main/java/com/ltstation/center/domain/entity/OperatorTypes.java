package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table operator_types (origine SQL Server : OperatorTypes). */
@Getter
@Setter
@Entity
@Table(name = "operator_types")
public class OperatorTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "operator_type_id")
    private Integer operatorTypeId;

    @Column(name = "operator_type_desc")
    private String operatorTypeDesc;

}
