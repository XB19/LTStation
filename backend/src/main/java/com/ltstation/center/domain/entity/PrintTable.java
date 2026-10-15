package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table print_table (origine SQL Server : PrintTable). */
@Getter
@Setter
@Entity
@Table(name = "print_table")
public class PrintTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "head")
    private String head;

    @Column(name = "tel")
    private String tel;

    @Column(name = "footer")
    private String footer;

}
