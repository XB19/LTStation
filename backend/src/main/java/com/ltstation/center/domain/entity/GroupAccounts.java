package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table group_accounts (origine SQL Server : GroupAccounts). */
@Getter
@Setter
@Entity
@Table(name = "group_accounts")
public class GroupAccounts {

    @Id
    @Column(name = "group_account_id")
    private Integer groupAccountId;

    @Column(name = "account_no")
    private String accountNo;

    @Column(name = "balance")
    private Double balance;

    @Column(name = "deposit_datetime")
    private LocalDateTime depositDatetime;

    @Column(name = "book_balance")
    private Double bookBalance;

    @Column(name = "consume_datetime")
    private LocalDateTime consumeDatetime;

    @Column(name = "load_up_limit")
    private Double loadUpLimit;

    @Column(name = "service_fee")
    private Double serviceFee;

    @Column(name = "update_datetime")
    private LocalDateTime updateDatetime;

    @Column(name = "status")
    private String status;

}
