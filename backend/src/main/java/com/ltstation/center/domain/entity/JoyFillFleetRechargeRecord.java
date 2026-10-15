package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_fleet_recharge_record (origine SQL Server : JoyFill_fleetRechargeRecord). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_fleet_recharge_record")
public class JoyFillFleetRechargeRecord {

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

    @Column(name = "recharge_time")
    private LocalDateTime rechargeTime;

    @Column(name = "before_balance")
    private BigDecimal beforeBalance;

    @Column(name = "recharge_amount")
    private BigDecimal rechargeAmount;

    @Column(name = "after_balance")
    private BigDecimal afterBalance;

    @Column(name = "login_name")
    private String loginName;

    @Column(name = "recharge_type")
    private Integer rechargeType;

    @Column(name = "account_type")
    private Integer accountType;

}
