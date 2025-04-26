// src/main/java/com/osman/traviaskbot/controller/ReservationController.java
package com.osman.traviaskbot.controller;

import com.osman.traviaskbot.dto.ReservationDto;
import com.osman.traviaskbot.entity.Reservation;
import com.osman.traviaskbot.entity.Route;
import com.osman.traviaskbot.service.ReservationProcessor;
import com.osman.traviaskbot.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ui.Model;


@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationRepository reservationRepo;
    private final ReservationProcessor processor;

    // listeleme: date filtresi artık LocalDate


    @GetMapping("/vrp-results")
public String showVrpResults(
@RequestParam(defaultValue = "1970-01-01")
@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after,
Model model
) {
List<Route> routes = processor.getVrpResults(after);
model.addAttribute("routes", routes);
return "vrp-results";
 }

    @PostMapping("/reservations/update")
    public String updateReservation(@ModelAttribute Reservation reservation) {
        // Geçerli rezervasyonu güncelle
        reservationRepo.save(reservation);  // JPA repository kullanarak rezervasyonu kaydediyoruz.
        return "redirect:/reservations";  // Rezervasyonlar sayfasına yönlendir.
    }



    // manuel tetikleme için artık GET de kullanabilirsiniz
    @GetMapping("/trigger")
    public Map<String, String> triggerGet() {
        processor.processReservations(); // sadece yeni rezervasyonları ekle
        return Map.of("status", "started");
    }

    // (isteğe bağlı: orijinal POST method’u da kalsın)
    @PostMapping("/trigger")
    public Map<String,String> triggerPost() {
        return triggerGet();
    }


    @PostMapping("/{id}/cancel")
    public ResponseEntity<String> cancelReservation(@PathVariable Long id) {
        return reservationRepo.findById(id).map(reservation -> {
            reservation.setStatus("cancelled");
            reservation.setCancelledAt(Instant.now());
            reservation.setCancelledBy("system"); // İleride giriş yapan kullanıcı adı da olabilir
            reservationRepo.save(reservation);
            return ResponseEntity.ok("Reservation cancelled successfully.");
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/uncancel")
    public ResponseEntity<String> uncancelReservation(@PathVariable Long id) {
        return reservationRepo.findById(id).map(reservation -> {
            reservation.setStatus("confirmed");
            reservation.setCancelledAt(null);
            reservation.setCancelledBy(null);
            reservationRepo.save(reservation);
            return ResponseEntity.ok("Reservation un-cancelled successfully.");
        }).orElse(ResponseEntity.notFound().build());
    }






    @PutMapping("/{id}/update")
    public Map<String, String> updateReservation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates
    ) {
        // 1. ID'ye göre rezervasyonu bul
        Reservation res = reservationRepo.findById(id).orElse(null);

        if (res == null) {
            return Map.of("status", "error", "message", "Reservation not found");
        }

        // 2. Gelen verilerle güncelle (hangi alanlar gelirse onları değiştir)
        if (updates.containsKey("customer")) {
            res.setCustomer((String) updates.get("customer"));
        }
        if (updates.containsKey("phone")) {
            res.setPhone((String) updates.get("phone"));
        }
        if (updates.containsKey("pickup")) {
            res.setPickup((String) updates.get("pickup"));
        }
        if (updates.containsKey("date")) {
            res.setDate(LocalDate.parse((String) updates.get("date")));
        }
        if (updates.containsKey("time")) {
            res.setTime(LocalTime.parse((String) updates.get("time")));
        }
        if (updates.containsKey("status")) {
            res.setStatus((String) updates.get("status"));
        }
        // İstersen adults / children gibi alanlar için de aynı şekilde ekleme yapabiliriz.

        // 3. Değişiklikleri kaydet
        reservationRepo.save(res);

        return Map.of("status", "success", "message", "Reservation updated");
    }



    @GetMapping("/cancelled")
    public List<ReservationDto> listCancelledReservations() {
        return reservationRepo.findByStatusOrderByDateAscTimeAsc("cancelled")
                .stream()
                .map(ReservationDto::of)
                .collect(Collectors.toList());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteReservation(@PathVariable Long id) {
        if (!reservationRepo.existsById(id)) {
            return Map.of("status", "error", "message", "Reservation not found");
        }

        reservationRepo.deleteById(id);
        return Map.of("status", "success", "message", "Reservation deleted");
    }


    @GetMapping("/active")
    public List<ReservationDto> getActiveReservations() {
        return reservationRepo.findByStatusOrderByDateAsc("confirmed")
                .stream()
                .map(ReservationDto::of)  // Reservation -> ReservationDto
                .toList();
    }


    @GetMapping("/confirmed")
    public List<ReservationDto> listConfirmedReservations() {
        return reservationRepo.findByStatusOrderByDateAscTimeAsc("confirmed")
                .stream()
                .map(ReservationDto::of)
                .collect(Collectors.toList());
    }


    @PostMapping("/new")
    public Map<String, String> createReservation(@RequestBody ReservationDto dto) {
        Reservation res = new Reservation();

        res.setCustomer(dto.getCustomer());
        res.setPhone(dto.getPhone());
        res.setPickup(dto.getPickup());
        res.setDate(dto.getDate());
        res.setTime(dto.getTime());
        res.setAdults(dto.getAdults());
        res.setChildren(dto.getChildren());
        res.setDistrict(dto.getDistrict());
        res.setReference(dto.getReference());
        res.setStatus(dto.getStatus() != null ? dto.getStatus() : "confirmed");

        reservationRepo.save(res);

        return Map.of("status", "success", "id", String.valueOf(res.getId()));
    }

    @PostMapping("/create")
    public Map<String, Object> createReservation(@RequestBody Map<String, Object> request) {
        Reservation res = new Reservation();

        // Gelen verilerden alanları dolduralım
        res.setCustomer((String) request.get("customer"));
        res.setPhone((String) request.get("phone"));
        res.setPickup((String) request.get("pickup"));
        res.setDistrict((String) request.get("district"));
        res.setDate(LocalDate.parse((String) request.get("date")));
        res.setTime(LocalTime.parse((String) request.get("time")));
        res.setStatus("confirmed"); // yeni rezervasyon default confirmed olacak
        res.setAdults((Integer) request.getOrDefault("adults", 1));
        res.setChildren((Integer) request.getOrDefault("children", 0));
        res.setReference((String) request.get("reference")); // isteğe bağlı, varsa alınacak

        reservationRepo.save(res);

        return Map.of(
                "status", "success",
                "id", res.getId()
        );
    }

    @PostMapping
    public Map<String, String> addReservation(@RequestBody ReservationDto dto) {
        Reservation res = new Reservation();
        res.setCustomer(dto.getCustomer());
        res.setPickup(dto.getPickup());
        res.setDistrict(dto.getDistrict());
        res.setDate(dto.getDate());
        res.setTime(dto.getTime());
        res.setStatus("confirmed"); // Yeni eklenenler confirmed olsun
        reservationRepo.save(res);
        return Map.of("status", "success", "id", res.getId().toString());
    }


}
