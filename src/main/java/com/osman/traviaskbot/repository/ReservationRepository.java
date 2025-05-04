package com.osman.traviaskbot.repository;

import com.osman.traviaskbot.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByReference(String reference);

    List<Reservation> findByDateGreaterThanEqualOrderByDateAscTimeAsc(LocalDate after);

    // 👇 VRP’de tur filtrelemek için
    List<Reservation> findByDateGreaterThanEqualAndTourOrderByDateAscTimeAsc(LocalDate after, String tour);

    // Diğer hazır sorgular
    List<Reservation> findByStatusOrderByDateAscTimeAsc(String status);
    List<Reservation> findByStatusOrderByDateAsc(String status);

    List<Reservation> findByDate(LocalDate date);

}
