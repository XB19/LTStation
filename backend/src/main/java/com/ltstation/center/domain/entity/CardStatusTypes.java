package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table card_status_types (origine SQL Server : CardStatusTypes). */
@Getter
@Setter
@Entity
@Table(name = "card_status_types")
public class CardStatusTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_status_type_id")
    private Integer cardStatusTypeId;

    @Column(name = "card_status_type_desc")
    private String cardStatusTypeDesc;

    @Column(name = "card_status_type_desc_en")
    private String cardStatusTypeDescEn;

}
