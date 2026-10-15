package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_fleet_list (origine SQL Server : JoyFill_fleetList). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_fleet_list")
public class JoyFillFleetList {

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

    @Column(name = "team_name")
    private String teamName;

    @Column(name = "account_state")
    private Integer accountState;

    @Column(name = "contact_person_id")
    private Integer contactPersonId;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "address")
    private String address;

    @Column(name = "remark")
    private String remark;

    @Column(name = "master_balance")
    private BigDecimal masterBalance;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "m_card_i_ds")
    private String mCardIDs;

    @Column(name = "account_type")
    private Integer accountType;

}
