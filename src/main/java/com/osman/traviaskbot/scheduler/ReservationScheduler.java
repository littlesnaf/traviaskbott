package com.osman.traviaskbot.scheduler;

import com.osman.traviaskbot.service.ReservationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationScheduler {

    private final ReservationProcessor processor;

    /** 30 dakikada bir çalışır */
    @Scheduled(fixedRateString = "PT30M")
    public void periodic() {
        log.info("⏰ Planlı toplama başladı");
        processor.processReservations();
    }
}
