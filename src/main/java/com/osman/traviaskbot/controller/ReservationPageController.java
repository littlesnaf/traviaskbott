package com.osman.traviaskbot.controller;

import com.osman.traviaskbot.entity.Reservation;
import com.osman.traviaskbot.entity.Route;
import com.osman.traviaskbot.repository.ReservationRepository;
import com.osman.traviaskbot.service.ReservationOptimizer; // ❗️Yeni eklenen import
import com.osman.traviaskbot.service.ReservationProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequiredArgsConstructor
public class ReservationPageController {

    private static final Logger logger = LoggerFactory.getLogger(ReservationPageController.class);
    private final ReservationRepository reservationRepository;
    private final ReservationOptimizer optimizer;
    private final ReservationProcessor processor;


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


    @GetMapping("/reservations")
    public String getReservations(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  Model model) {
        List<Reservation> reservations;
        if (date != null) {
            reservations = reservationRepository.findByDate(date);
        } else {
            reservations = reservationRepository.findAll();
        }
        model.addAttribute("reservations", reservations);
        model.addAttribute("date", date);
        return "reservations";
    }

    @GetMapping("/reservations/by-date")
    public List<Reservation> getReservationsByDate(@RequestParam String date) {
        LocalDate parsedDate = LocalDate.parse(date); // ISO format: yyyy-MM-dd
        return reservationRepository.findByDate(parsedDate);
    }


    @GetMapping("/reservations/edit/{id}")
    public String editReservation(@PathVariable Long id, Model model) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Geçersiz ID: " + id));
        model.addAttribute("reservation", reservation);
        return "edit-reservation";
    }

    @PostMapping("/reservations/update")
    public String updateReservation(@ModelAttribute Reservation reservation) {
        reservationRepository.save(reservation);
        return "redirect:/reservations";
    }

    @PostMapping("/optimize")
    public String optimizeRoutes() {
        List<Reservation> reservations = reservationRepository.findAll(); // ⭐ Verileri çek
        optimizer.optimize(reservations); // ⭐ Verileri optimize metoduna ver
        logger.info("✅ Optimize tetiklendi!");
        return "redirect:/reservations";
    }






}
