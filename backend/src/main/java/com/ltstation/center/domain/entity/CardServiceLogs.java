package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table card_service_logs (origine SQL Server : CardServiceLogs). */
@Getter
@Setter
@Entity
@Table(name = "card_service_logs")
public class CardServiceLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_service_log_id")
    private Integer cardServiceLogId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "process_code")
    private String processCode;

    @Column(name = "transaction_type_id")
    private Integer transactionTypeId;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "discount_amount")
    private Double discountAmount;

    @Column(name = "ctc")
    private Double ctc;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "trans_datetime")
    private LocalDateTime transDatetime;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "memo")
    private String memo;

}
