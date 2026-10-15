package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table gprsiccid (origine SQL Server : GPRSICCID). */
@Getter
@Setter
@Entity
@Table(name = "gprsiccid")
public class GPRSICCID {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "iccid_type")
    private Integer iccidType;

    @Column(name = "iccid_length")
    private Integer iccidLength;

    @Column(name = "name")
    private String name;

}
