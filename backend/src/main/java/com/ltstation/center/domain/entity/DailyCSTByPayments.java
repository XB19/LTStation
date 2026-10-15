package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table daily_cst_by_payments (origine SQL Server : DailyCSTByPayments). */
@Getter
@Setter
@Entity
@Table(name = "daily_cst_by_payments")
public class DailyCSTByPayments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "account_datetime")
    private LocalDateTime accountDatetime;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "group_user_no")
    private String groupUserNo;

    @Column(name = "presale_cash")
    private Double presaleCash;

    @Column(name = "presale_check")
    private Double presaleCheck;

    @Column(name = "desposit_cash")
    private Double despositCash;

    @Column(name = "desposit_check")
    private Double despositCheck;

    @Column(name = "desposit_transfer")
    private Double despositTransfer;

    @Column(name = "fee_cash")
    private Double feeCash;

    @Column(name = "fee_check")
    private Double feeCheck;

    @Column(name = "fee_transfer")
    private Double feeTransfer;

    @Column(name = "payout_cash")
    private Double payoutCash;

    @Column(name = "payout_check")
    private Double payoutCheck;

}
