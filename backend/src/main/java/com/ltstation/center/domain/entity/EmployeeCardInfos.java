package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table employee_card_infos (origine SQL Server : EmployeeCardInfos). */
@Getter
@Setter
@Entity
@Table(name = "employee_card_infos")
public class EmployeeCardInfos {

    @Id
    @Column(name = "asn")
    private String asn;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "card_type_id")
    private Integer cardTypeId;

    @Column(name = "cipher_flg")
    private String cipherFlg;

    @Column(name = "issue_datetime")
    private LocalDateTime issueDatetime;

    @Column(name = "valid_datetime")
    private LocalDateTime validDatetime;

    @Column(name = "memo1")
    private String memo1;

    @Column(name = "customer_account_id")
    private Integer customerAccountId;

    @Column(name = "card_status_type_id")
    private Integer cardStatusTypeId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "special_price")
    private String specialPrice;

    @Column(name = "open_accoun_emp_name")
    private String openAccounEmpName;

    @Column(name = "open_station_id")
    private Integer openStationId;

    @Column(name = "company_id")
    private Integer companyId;

}
