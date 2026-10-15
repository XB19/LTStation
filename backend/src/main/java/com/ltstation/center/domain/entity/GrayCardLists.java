package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gray_card_lists (origine SQL Server : GrayCardLists). */
@Getter
@Setter
@Entity
@Table(name = "gray_card_lists")
public class GrayCardLists {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gray_card_list_id")
    private Integer grayCardListId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "balance")
    private Double balance;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "ctc")
    private String ctc;

    @Column(name = "ds")
    private Integer ds;

    @Column(name = "account_time")
    private LocalDateTime accountTime;

    @Column(name = "gmac")
    private String gmac;

    @Column(name = "psamtid")
    private String psamtid;

    @Column(name = "psamttc")
    private String psamttc;

}
