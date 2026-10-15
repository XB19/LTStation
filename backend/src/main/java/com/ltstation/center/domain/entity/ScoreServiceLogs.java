package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table score_service_logs (origine SQL Server : ScoreServiceLogs). */
@Getter
@Setter
@Entity
@Table(name = "score_service_logs")
public class ScoreServiceLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_service_log_id")
    private Integer scoreServiceLogId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "score_trans_type_id")
    private Integer scoreTransTypeId;

    @Column(name = "trans_date_time")
    private LocalDateTime transDateTime;

    @Column(name = "score_initial")
    private Double scoreInitial;

    @Column(name = "score_deposit")
    private Double scoreDeposit;

    @Column(name = "score_consume")
    private Double scoreConsume;

    @Column(name = "score_balance")
    private Double scoreBalance;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "memo")
    private String memo;

}
