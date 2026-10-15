package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table commodity_prices (origine SQL Server : CommodityPrices). */
@Getter
@Setter
@Entity
@Table(name = "commodity_prices")
@IdClass(CommodityPrices.Pk.class)
public class CommodityPrices {

    @Id
    @Column(name = "station_id")
    private Integer stationId;

    @Id
    @Column(name = "commodity_id")
    private Integer commodityId;

    @Column(name = "price1")
    private Double price1;

    @Column(name = "price2")
    private Double price2;

    @Column(name = "price3")
    private Double price3;

    @Column(name = "price4")
    private Double price4;

    @Column(name = "price5")
    private Double price5;

    @Column(name = "price6")
    private Double price6;

    @Column(name = "price7")
    private Double price7;

    @Column(name = "price8")
    private Double price8;

    @Column(name = "price9")
    private Double price9;

    @Column(name = "price10")
    private Double price10;

    @Column(name = "price11")
    private Double price11;

    @Column(name = "price12")
    private Double price12;

    @Column(name = "new_price1")
    private Double newPrice1;

    @Column(name = "new_price2")
    private Double newPrice2;

    @Column(name = "new_price3")
    private Double newPrice3;

    @Column(name = "new_price4")
    private Double newPrice4;

    @Column(name = "new_price5")
    private Double newPrice5;

    @Column(name = "new_price6")
    private Double newPrice6;

    @Column(name = "new_price7")
    private Double newPrice7;

    @Column(name = "new_price8")
    private Double newPrice8;

    @Column(name = "new_price9")
    private Double newPrice9;

    @Column(name = "validate_time")
    private LocalDateTime validateTime;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer stationId;
        private Integer commodityId;
    }
}
