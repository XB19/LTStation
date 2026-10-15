package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table sprice_price_set (origine SQL Server : SpricePriceSet). */
@Getter
@Setter
@Entity
@Table(name = "sprice_price_set")
public class SpricePriceSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "grade_id")
    private Integer gradeId;

    @Column(name = "sprice_price")
    private BigDecimal spricePrice;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "is_out")
    private Boolean isOut;

}
