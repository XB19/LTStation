package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table check_dailies (origine SQL Server : CheckDailies). */
@Getter
@Setter
@Entity
@Table(name = "check_dailies")
public class CheckDailies {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_daily_id")
    private Integer checkDailyId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "date_check")
    private LocalDateTime dateCheck;

    @Column(name = "commodity_id")
    private Integer commodityId;

    @Column(name = "commodity_name")
    private String commodityName;

    @Column(name = "quantity_begin")
    private Double quantityBegin;

    @Column(name = "amount_begin")
    private Double amountBegin;

    @Column(name = "quantity_in")
    private Double quantityIn;

    @Column(name = "amount_in")
    private Double amountIn;

    @Column(name = "quantity_out")
    private Double quantityOut;

    @Column(name = "amount_out")
    private Double amountOut;

}
