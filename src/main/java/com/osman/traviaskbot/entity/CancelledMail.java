// src/main/java/com/osman/traviaskbot/entity/CancelledMail.java
package com.osman.traviaskbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "cancelled_mails")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelledMail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 4000)
    private String body;

    private Instant receivedAt;
}
