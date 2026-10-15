package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table air_get_ol_d_transaction (origine SQL Server : AirGetOlDTransaction). */
@Getter
@Setter
@Entity
@Table(name = "air_get_ol_d_transaction")
public class AirGetOlDTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "ps_start")
    private Integer psStart;

    @Column(name = "ps_end")
    private Integer psEnd;

}
