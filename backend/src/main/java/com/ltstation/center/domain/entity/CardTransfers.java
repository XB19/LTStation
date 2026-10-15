package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table card_transfers (origine SQL Server : CardTransfers). */
@Getter
@Setter
@Entity
@Table(name = "card_transfers")
public class CardTransfers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "asn")
    private String asn;

    @Column(name = "group_card_asn")
    private String groupCardAsn;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "vehicle_no")
    private String vehicleNo;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "memo")
    private String memo;

    @Column(name = "group_id")
    private Integer groupId;

}
