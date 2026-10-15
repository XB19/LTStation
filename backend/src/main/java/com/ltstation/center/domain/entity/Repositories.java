package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table repositories (origine SQL Server : Repositories). */
@Getter
@Setter
@Entity
@Table(name = "repositories")
public class Repositories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repository_id")
    private Integer repositoryId;

    @Column(name = "repository_desc")
    private String repositoryDesc;

    @Column(name = "repository_category")
    private String repositoryCategory;

    @Column(name = "in_used")
    private Boolean inUsed;

}
