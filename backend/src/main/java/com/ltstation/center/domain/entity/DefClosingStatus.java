package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_closing_status (origine SQL Server : DefClosingStatus). */
@Getter
@Setter
@Entity
@Table(name = "def_closing_status")
public class DefClosingStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "closing_status_id")
    private Integer closingStatusId;

    @Column(name = "closing_status_desc")
    private String closingStatusDesc;

    @Column(name = "closing_status_desc_en")
    private String closingStatusDescEn;

}
