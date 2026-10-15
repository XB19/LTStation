package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table station_deposit_log (origine SQL Server : StationDepositLog). */
@Getter
@Setter
@Entity
@Table(name = "station_deposit_log")
public class StationDepositLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "operate")
    private Integer operate;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "bal")
    private BigDecimal bal;

    @Column(name = "create_time")
    private Long createTime;

}
