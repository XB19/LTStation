package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table procurement_details (origine SQL Server : ProcurementDetails). */
@Getter
@Setter
@Entity
@Table(name = "procurement_details")
@IdClass(ProcurementDetails.Pk.class)
public class ProcurementDetails {

    @Id
    @Column(name = "procurement_id")
    private Integer procurementId;

    @Id
    @Column(name = "commodity_id")
    private Integer commodityId;

    @Column(name = "unit_measurement")
    private String unitMeasurement;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "price_unit")
    private Double priceUnit;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer procurementId;
        private Integer commodityId;
    }
}
