package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table grey_card_unlock_infos (origine SQL Server : GreyCardUnlockInfos). */
@Getter
@Setter
@Entity
@Table(name = "grey_card_unlock_infos")
public class GreyCardUnlockInfos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "asn")
    private String asn;

    @Column(name = "balance")
    private Double balance;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "ctc")
    private String ctc;

    @Column(name = "ds")
    private String ds;

    @Column(name = "g_time")
    private LocalDateTime gTime;

    @Column(name = "psam_tid")
    private String psamTid;

    @Column(name = "psam_ttc")
    private String psamTtc;

    @Column(name = "gmac2")
    private String gmac2;

    @Column(name = "gtac")
    private String gtac;

    @Column(name = "octc")
    private String octc;

    @Column(name = "mtod")
    private String mtod;

    @Column(name = "d_mtime")
    private LocalDateTime dMtime;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "memo")
    private String memo;

}
