package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table delete_black_lists (origine SQL Server : DeleteBlackLists). */
@Getter
@Setter
@Entity
@Table(name = "delete_black_lists")
public class DeleteBlackLists {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delete_black_list_id")
    private Integer deleteBlackListId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "time")
    private LocalDateTime time;

}
