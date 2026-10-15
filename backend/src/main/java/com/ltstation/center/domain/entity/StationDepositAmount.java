package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table station_deposit_amount (origine SQL Server : StationDepositAmount). */
@Getter
@Setter
@Entity
@Table(name = "station_deposit_amount")
public class StationDepositAmount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_value_code")
    private String stationValueCode;

    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    @Column(name = "update_time")
    private Long updateTime;

}
