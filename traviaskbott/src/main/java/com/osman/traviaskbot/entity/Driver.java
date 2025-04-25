package com.osman.traviaskbot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String licensePlate;

    private double latitude;
    private double longitude;

    private int capacity;
}
