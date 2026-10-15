package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table clearinginfoes (origine SQL Server : Clearinginfoes). */
@Getter
@Setter
@Entity
@Table(name = "clearinginfoes")
public class Clearinginfoes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "log_datetime")
    private LocalDateTime logDatetime;

    @Column(name = "asn")
    private String asn;

    @Column(name = "ctc")
    private String ctc;

    @Column(name = "ttype")
    private Integer ttype;

    @Column(name = "t_datetime")
    private LocalDateTime tDatetime;

    @Column(name = "ds")
    private Integer ds;

    @Column(name = "amn")
    private Double amn;

    @Column(name = "bal")
    private Double bal;

    @Column(name = "account_bal")
    private Double accountBal;

    @Column(name = "status")
    private Integer status;

    @Column(name = "memo")
    private String memo;

}
