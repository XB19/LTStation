package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table white_card_lists (origine SQL Server : WhiteCardLists). */
@Getter
@Setter
@Entity
@Table(name = "white_card_lists")
public class WhiteCardLists {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "white_card_list_id")
    private Integer whiteCardListId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "validate_time")
    private LocalDateTime validateTime;

    @Column(name = "station_id")
    private Integer stationId;

}
