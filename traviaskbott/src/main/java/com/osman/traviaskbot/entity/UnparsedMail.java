package com.osman.traviaskbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "unparsed_mails")
@Data
@NoArgsConstructor @AllArgsConstructor
public class UnparsedMail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 4000)      // gövde uzun olabilir
    private String body;
    private Instant receivedAt;
}
