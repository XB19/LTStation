package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table account_service_logs (origine SQL Server : AccountServiceLogs). */
@Getter
@Setter
@Entity
@Table(name = "account_service_logs")
public class AccountServiceLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "transaction_type_id")
    private Integer transactionTypeId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "card_type_id")
    private Integer cardTypeId;

    @Column(name = "account_no")
    private String accountNo;

    @Column(name = "process_code")
    private String processCode;

    @Column(name = "card_service_log_id")
    private Integer cardServiceLogId;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "fee_amount")
    private Double feeAmount;

    @Column(name = "discount_amount")
    private Double discountAmount;

    @Column(name = "account_datetime")
    private LocalDateTime accountDatetime;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "cheque_no")
    private String chequeNo;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "terminal_no")
    private String terminalNo;

    @Column(name = "trans_datetime")
    private LocalDateTime transDatetime;

    @Column(name = "balance")
    private Double balance;

    @Column(name = "credit_count_id")
    private String creditCountId;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "company_id")
    private Integer companyId;

}
