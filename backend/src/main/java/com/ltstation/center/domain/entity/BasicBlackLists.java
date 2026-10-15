package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table basic_black_lists (origine SQL Server : BasicBlackLists). */
@Getter
@Setter
@Entity
@Table(name = "basic_black_lists")
public class BasicBlackLists {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "basic_black_list_id")
    private Integer basicBlackListId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "time")
    private LocalDateTime time;

}
