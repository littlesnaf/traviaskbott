package com.osman.traviaskbot.repository;

import com.osman.traviaskbot.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByDateGreaterThanEqualOrderByDateAscTimeAsc(LocalDate date);
    Optional<Reservation> findByReference(String reference);
}
