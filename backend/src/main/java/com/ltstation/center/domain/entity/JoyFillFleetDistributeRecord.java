package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_fleet_distribute_record (origine SQL Server : JoyFill_fleetDistributeRecord). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_fleet_distribute_record")
public class JoyFillFleetDistributeRecord {

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

    @Column(name = "distribute_time")
    private LocalDateTime distributeTime;

    @Column(name = "recharge_type")
    private Integer rechargeType;

    @Column(name = "change_amount")
    private BigDecimal changeAmount;

    @Column(name = "master_before_balance")
    private BigDecimal masterBeforeBalance;

    @Column(name = "master_after_balance")
    private BigDecimal masterAfterBalance;

    @Column(name = "driver_before_balance")
    private BigDecimal driverBeforeBalance;

    @Column(name = "driver_after_balance")
    private BigDecimal driverAfterBalance;

    @Column(name = "login_name")
    private String loginName;

    @Column(name = "account_type")
    private Integer accountType;

}
