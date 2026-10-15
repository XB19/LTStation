package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table group_tansfers (origine SQL Server : GroupTansfers). */
@Getter
@Setter
@Entity
@Table(name = "group_tansfers")
public class GroupTansfers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "asn")
    private String asn;

    @Column(name = "driver")
    private String driver;

    @Column(name = "limit_vehicle_no")
    private String limitVehicleNo;

    @Column(name = "account_log_id")
    private Integer accountLogId;

    @Column(name = "card_log_id")
    private Integer cardLogId;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "group_account_balance")
    private Double groupAccountBalance;

    @Column(name = "driver_account_balance")
    private Double driverAccountBalance;

    @Column(name = "account_datetime")
    private String accountDatetime;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "trans_datetime")
    private LocalDateTime transDatetime;

    @Column(name = "status")
    private String status;

    @Column(name = "memo")
    private String memo;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "group_card_asn")
    private String groupCardAsn;

}
