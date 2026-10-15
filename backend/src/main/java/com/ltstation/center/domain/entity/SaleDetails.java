package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table sale_details (origine SQL Server : SaleDetails). */
@Getter
@Setter
@Entity
@Table(name = "sale_details")
@IdClass(SaleDetails.Pk.class)
public class SaleDetails {

    @Id
    @Column(name = "sale_id")
    private Integer saleId;

    @Id
    @Column(name = "sn")
    private Integer sn;

    @Column(name = "commodity_id")
    private Integer commodityId;

    @Column(name = "commodity_name")
    private String commodityName;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "price_unit")
    private Double priceUnit;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer saleId;
        private Integer sn;
    }
}
