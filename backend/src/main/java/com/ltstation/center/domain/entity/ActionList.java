package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table action_list (origine SQL Server : ActionList). */
@Getter
@Setter
@Entity
@Table(name = "action_list")
public class ActionList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "user_ip")
    private String userIp;

    @Column(name = "action_name")
    private String actionName;

    @Column(name = "action_time")
    private LocalDateTime actionTime;

    @Column(name = "memo")
    private String memo;

}
