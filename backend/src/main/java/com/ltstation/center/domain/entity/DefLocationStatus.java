package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_location_status (origine SQL Server : DefLocationStatus). */
@Getter
@Setter
@Entity
@Table(name = "def_location_status")
public class DefLocationStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_status_id")
    private Integer locationStatusId;

    @Column(name = "location_status_desc")
    private String locationStatusDesc;

}
