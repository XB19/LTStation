package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_fleet_driver (origine SQL Server : JoyFill_fleetDriver). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_fleet_driver")
public class JoyFillFleetDriver {

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

    @Column(name = "card_state")
    private Integer cardState;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "number_plate")
    private String numberPlate;

    @Column(name = "remark")
    private String remark;

    @Column(name = "balance")
    private BigDecimal balance;

    @Column(name = "max_oil_value")
    private BigDecimal maxOilValue;

    @Column(name = "m_card_id")
    private String mCardId;

    @Column(name = "password")
    private String password;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "asn")
    private String asn;

    @Column(name = "account_type")
    private Integer accountType;

}
