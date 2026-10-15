package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_payments (origine SQL Server : DefPayments). */
@Getter
@Setter
@Entity
@Table(name = "def_payments")
public class DefPayments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "payment_desc")
    private String paymentDesc;

}
