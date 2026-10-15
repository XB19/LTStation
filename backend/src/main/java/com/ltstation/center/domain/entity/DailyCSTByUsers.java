package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table daily_cst_by_users (origine SQL Server : DailyCSTByUsers). */
@Getter
@Setter
@Entity
@Table(name = "daily_cst_by_users")
public class DailyCSTByUsers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "account_datetime")
    private LocalDateTime accountDatetime;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "terminal_no")
    private String terminalNo;

    @Column(name = "group_amount")
    private Double groupAmount;

    @Column(name = "personal_amount")
    private Double personalAmount;

    @Column(name = "desposit_group_amount")
    private Double despositGroupAmount;

    @Column(name = "desposit_personal_amount")
    private Double despositPersonalAmount;

    @Column(name = "fee_group_amount")
    private Double feeGroupAmount;

    @Column(name = "fee_personal_amount")
    private Double feePersonalAmount;

    @Column(name = "payout_group_amount")
    private Double payoutGroupAmount;

    @Column(name = "payout_personal_amount")
    private Double payoutPersonalAmount;

}
