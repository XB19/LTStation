package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table customer_accounts (origine SQL Server : CustomerAccounts). */
@Getter
@Setter
@Entity
@Table(name = "customer_accounts")
public class CustomerAccounts {

    @Id
    @Column(name = "customer_account_id")
    private Integer customerAccountId;

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

    @Column(name = "total_times_daily")
    private Integer totalTimesDaily;

    @Column(name = "total_amount_daily")
    private Double totalAmountDaily;

    @Column(name = "total_amount_monthly")
    private Double totalAmountMonthly;

    @Column(name = "total_volume_monthly")
    private Double totalVolumeMonthly;

    @Column(name = "mileage_lastest")
    private Double mileageLastest;

    @Column(name = "volume_lastest")
    private Double volumeLastest;

    @Column(name = "trans_time")
    private LocalDateTime transTime;

    @Column(name = "total_times_daily_store")
    private Integer totalTimesDailyStore;

    @Column(name = "total_amount_daily_store")
    private Double totalAmountDailyStore;

    @Column(name = "total_amount_monthly_store")
    private Double totalAmountMonthlyStore;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "np_value")
    private String npValue;

}
