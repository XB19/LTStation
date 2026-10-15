package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_trashed_status (origine SQL Server : DefTrashedStatus). */
@Getter
@Setter
@Entity
@Table(name = "def_trashed_status")
public class DefTrashedStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trashed_status_id")
    private Integer trashedStatusId;

    @Column(name = "trashed_status_desc")
    private String trashedStatusDesc;

}
