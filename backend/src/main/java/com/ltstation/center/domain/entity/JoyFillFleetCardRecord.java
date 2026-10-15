package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_fleet_card_record (origine SQL Server : JoyFill_fleetCardRecord). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_fleet_card_record")
public class JoyFillFleetCardRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "team_id")
    private Integer teamId;

    @Column(name = "team_name")
    private String teamName;

    @Column(name = "driver_id")
    private Integer driverId;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "m_card_id")
    private String mCardId;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "liters")
    private BigDecimal liters;

    @Column(name = "change_amount")
    private BigDecimal changeAmount;

    @Column(name = "before_balance")
    private BigDecimal beforeBalance;

    @Column(name = "after_balance")
    private BigDecimal afterBalance;

    @Column(name = "change_time")
    private LocalDateTime changeTime;

    @Column(name = "energy_id")
    private Integer energyId;

    @Column(name = "energy_name")
    private String energyName;

    @Column(name = "total_liters")
    private BigDecimal totalLiters;

    @Column(name = "account_type")
    private Integer accountType;

    @Column(name = "consume_station_id")
    private Integer consumeStationId;

    @Column(name = "consume_station_name")
    private String consumeStationName;

    @Column(name = "number_plate")
    private String numberPlate;

}
