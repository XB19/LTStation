package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table data (origine SQL Server : Data). */
@Getter
@Setter
@Entity
@Table(name = "data")
public class Data {

    @Column(name = "id")
    private Integer id;

    @Column(name = "d_type")
    private String dType;

    @Column(name = "d_ver")
    private String dVer;

    @Column(name = "d_index")
    private String dIndex;

    @Column(name = "d_data")
    private String dData;

    @Column(name = "company_id")
    private Integer companyId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_index")
    private Integer idIndex;

}
