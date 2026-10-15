package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table gprs_real_data (origine SQL Server : GPRSRealData). */
@Getter
@Setter
@Entity
@Table(name = "gprs_real_data")
public class GPRSRealData {

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "volume")
    private BigDecimal volume;

    @Id
    @Column(name = "noz_id")
    private Integer nozId;

}
