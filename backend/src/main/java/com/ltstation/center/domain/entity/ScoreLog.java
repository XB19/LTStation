package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table score_log (origine SQL Server : ScoreLog). */
@Getter
@Setter
@Entity
@Table(name = "score_log")
public class ScoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "customer_account_id")
    private Integer customerAccountId;

    @Column(name = "current_score")
    private BigDecimal currentScore;

    @Column(name = "deal_score")
    private BigDecimal dealScore;

    @Column(name = "score_balance")
    private BigDecimal scoreBalance;

    @Column(name = "create_time")
    private LocalDateTime createTime;

}
