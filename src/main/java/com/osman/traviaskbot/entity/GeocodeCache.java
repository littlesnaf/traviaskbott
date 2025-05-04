package com.osman.traviaskbot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "geocode_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String address;

    private double lat;
    private double lng;

    public GeocodeCache(String address, double lat, double lng) {
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }
}
