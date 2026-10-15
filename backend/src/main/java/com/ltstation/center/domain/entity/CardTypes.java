package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table card_types (origine SQL Server : CardTypes). */
@Getter
@Setter
@Entity
@Table(name = "card_types")
public class CardTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_type_id")
    private Integer cardTypeId;

    @Column(name = "card_type_desc")
    private String cardTypeDesc;

    @Column(name = "card_type_desc_en")
    private String cardTypeDescEn;

}
