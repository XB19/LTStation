package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table stocktakings (origine SQL Server : Stocktakings). */
@Getter
@Setter
@Entity
@Table(name = "stocktakings")
public class Stocktakings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stocktaking_id")
    private Integer stocktakingId;

    @Column(name = "date_create")
    private LocalDateTime dateCreate;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "commodity_id")
    private Integer commodityId;

    @Column(name = "quantity_book")
    private Double quantityBook;

    @Column(name = "quantity_physical")
    private Double quantityPhysical;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "memo")
    private String memo;

}
