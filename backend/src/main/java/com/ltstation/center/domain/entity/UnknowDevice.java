package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table unknow_device (origine SQL Server : UnknowDevice). */
@Getter
@Setter
@Entity
@Table(name = "unknow_device")
public class UnknowDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "un_know_device_no")
    private String unKnowDeviceNo;

    @Column(name = "date_time")
    private LocalDateTime dateTime;

}
