package com.osman.traviaskbot.controller;

import com.osman.traviaskbot.entity.Reservation;
import com.osman.traviaskbot.repository.ReservationRepository;
import com.osman.traviaskbot.service.ReservationOptimizer; // ❗️Yeni eklenen import
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequiredArgsConstructor
public class ReservationPageController {

    private static final Logger logger = LoggerFactory.getLogger(ReservationPageController.class);
    private final ReservationRepository reservationRepository;
    private final ReservationOptimizer reservationOptimizer; // ❗️Yeni eklenen alan
    private final ReservationOptimizer optimizer;

    @GetMapping("/reservations")
    public String showReservations(Model model) {
        List<Reservation> reservations = reservationRepository.findAll();
        model.addAttribute("reservations", reservations);
        return "reservations";
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
