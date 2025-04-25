// src/main/java/com/osman/traviaskbot/controller/ReservationController.java
package com.osman.traviaskbot.controller;

import com.osman.traviaskbot.dto.ReservationDto;
import com.osman.traviaskbot.entity.Reservation;
import com.osman.traviaskbot.entity.Route;
import com.osman.traviaskbot.service.ReservationProcessor;
import com.osman.traviaskbot.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    @GetMapping
    public Map<LocalDate, List<ReservationDto>> list(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate after
    ) {
        List<ReservationDto> dtos = reservationRepo
                .findByDateGreaterThanEqualOrderByDateAscTimeAsc(after)
                .stream()
                .map(ReservationDto::of)
                .collect(Collectors.toList());

        return dtos.stream()
                .collect(Collectors.groupingBy(
                        ReservationDto::getDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    @GetMapping("/vrp-results")
    public String showVrpResults(Model model) {
        // VRP işlemi sonucu dönen rotalar
        List<Route> routes = processor.getVrpResults(); // VRP sonucu döndürülecek veriyi burada alıyoruz.
        model.addAttribute("routes", routes);
        return "vrp-results";  // Bu, vrp-results.html şablonunu kullanacak.
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
}
