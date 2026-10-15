package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table transaction_types (origine SQL Server : TransactionTypes). */
@Getter
@Setter
@Entity
@Table(name = "transaction_types")
public class TransactionTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_type_id")
    private Integer transactionTypeId;

    @Column(name = "transaction_type_desc")
    private String transactionTypeDesc;

    @Column(name = "transaction_type_desc_en")
    private String transactionTypeDescEn;

}
