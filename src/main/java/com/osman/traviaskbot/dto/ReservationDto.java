// src/main/java/com/osman/traviaskbot/dto/ReservationDto.java
package com.osman.traviaskbot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.osman.traviaskbot.entity.Reservation;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@JsonPropertyOrder({
        "district","status","reference","date","time",
        "adults","children","customer","phone","pickup"
})
public class ReservationDto {
    private String district;
    private String status;
    private String reference;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime time;

    private int adults;
    private int children;
    private String customer;
    private String phone;
    private String pickup;

    public static ReservationDto of(Reservation r) {
        ReservationDto d = new ReservationDto();
        d.setDistrict(r.getDistrict());
        d.setStatus(r.getStatus());
        d.setReference(r.getReference());
        d.setDate(r.getDate());
        d.setTime(r.getTime());
        d.setAdults(r.getAdults());
        d.setChildren(r.getChildren());
        d.setCustomer(r.getCustomer());
        d.setPhone(r.getPhone());
        d.setPickup(r.getPickup());
        return d;
    }
}
