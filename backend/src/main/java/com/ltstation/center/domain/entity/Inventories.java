package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table inventories (origine SQL Server : Inventories). */
@Getter
@Setter
@Entity
@Table(name = "inventories")
@IdClass(Inventories.Pk.class)
public class Inventories {

    @Id
    @Column(name = "commodity_id")
    private Integer commodityId;

    @Id
    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "in_quantity")
    private Double inQuantity;

    @Column(name = "out_quantity")
    private Double outQuantity;

    @Column(name = "date_change")
    private LocalDateTime dateChange;

    @Column(name = "memo")
    private String memo;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer commodityId;
        private Integer stationId;
    }
}
