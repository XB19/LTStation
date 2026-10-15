package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table pre_card_no (origine SQL Server : PreCardNo). */
@Getter
@Setter
@Entity
@Table(name = "pre_card_no")
public class PreCardNo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "asn")
    private String asn;

}
