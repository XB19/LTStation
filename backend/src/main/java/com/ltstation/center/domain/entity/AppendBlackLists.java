package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table append_black_lists (origine SQL Server : AppendBlackLists). */
@Getter
@Setter
@Entity
@Table(name = "append_black_lists")
public class AppendBlackLists {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "append_black_list_id")
    private Integer appendBlackListId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "time")
    private LocalDateTime time;

    @Column(name = "deal")
    private Boolean deal;

}
