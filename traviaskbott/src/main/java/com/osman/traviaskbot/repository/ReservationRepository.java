    // src/main/java/com/osman/traviaskbot/repository/ReservationRepository.java
    package com.osman.traviaskbot.repository;

    import com.osman.traviaskbot.entity.Reservation;
    import org.springframework.data.jpa.repository.JpaRepository;

    import java.time.LocalDate;
    import java.util.List;

    public interface ReservationRepository
            extends JpaRepository<Reservation, Long> {

        List<Reservation> findByDateGreaterThanEqualOrderByDateAscTimeAsc(LocalDate after);

        List<Reservation> findByStatusOrderByDateAscTimeAsc(String status);
        List<Reservation> findByStatusOrderByDateAsc(String status);

    }
