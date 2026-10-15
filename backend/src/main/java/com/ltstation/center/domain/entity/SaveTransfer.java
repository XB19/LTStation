package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table save_transfer (origine SQL Server : SaveTransfer). */
@Getter
@Setter
@Entity
@Table(name = "save_transfer")
public class SaveTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "st_type")
    private Integer stType;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "save_to_asn")
    private String saveToAsn;

    @Column(name = "is_using")
    private Boolean isUsing;

}
