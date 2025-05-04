package com.osman.traviaskbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reservations")
@Data
public class Reservation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reference;
    private String status;

    @Column(length = 3000)
    private String tour;          // 💡 Yeni

    @Column(name = "option_name", length = 3000)
    private String optionName;    // 💡 Yeni

    private LocalDate date;
    private LocalTime time;

    private int adults;
    private int children;

    @Column(length = 500)
    private String customer;

    private String phone;

    @Column(length = 2000)
    private String pickup;
    private String district;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private String cancelledBy;
}
