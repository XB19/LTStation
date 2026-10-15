package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table card_maker_logs (origine SQL Server : CardMakerLogs). */
@Getter
@Setter
@Entity
@Table(name = "card_maker_logs")
public class CardMakerLogs {

    @Id
    @Column(name = "asn")
    private String asn;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "action_time")
    private LocalDateTime actionTime;

}
