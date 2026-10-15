package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table bank_get_response (origine SQL Server : Bank_GetResponse). */
@Getter
@Setter
@Entity
@Table(name = "bank_get_response")
public class BankGetResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "tit_amount")
    private String titAmount;

    @Column(name = "tit_txndatetime")
    private String titTxndatetime;

    @Column(name = "tit_hostresponse_str")
    private String titHostresponseStr;

    @Column(name = "tit_hostresponse_authcode")
    private String titHostresponseAuthcode;

    @Column(name = "tit_stan")
    private String titStan;

    @Column(name = "tit_rrn")
    private String titRrn;

    @Column(name = "authorization_status")
    private String authorizationStatus;

    @Column(name = "tit_external_client_tran_id")
    private String titExternalClientTranId;

    @Column(name = "tit_serialno")
    private String titSerialno;

    @Column(name = "transaction_deal_id")
    private Integer transactionDealId;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "emp_id")
    private Integer empId;

}
