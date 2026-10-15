package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_currencies (origine SQL Server : DefCurrencies). */
@Getter
@Setter
@Entity
@Table(name = "def_currencies")
public class DefCurrencies {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "currency_id")
    private Integer currencyId;

    @Column(name = "currency_desc")
    private String currencyDesc;

}
