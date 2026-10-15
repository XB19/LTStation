package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table user_profile (origine SQL Server : UserProfile). */
@Getter
@Setter
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "user_name")
    private String userName;

}
