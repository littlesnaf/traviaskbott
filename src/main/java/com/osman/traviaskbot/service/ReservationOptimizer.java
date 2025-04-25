package com.osman.traviaskbot.service;

import com.osman.traviaskbot.entity.Reservation;
import com.osman.traviaskbot.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationOptimizer {

    private final ReservationRepository reservationRepository;
    private final GeocodingService geocodingService;
    private final VrpService vrpService;

    public void optimize(List<Reservation> reservations) {
        log.info("\uD83D\uDEA0 Optimize ediliyor, toplam rezervasyon: {}", reservations.size());

        // Geçerli pickup adresi olanları filtrele
        List<Reservation> validReservations = reservations.stream()
                .filter(r -> r.getPickup() != null && !r.getPickup().isBlank())
                .collect(Collectors.toList());

        int skippedCount = reservations.size() - validReservations.size();
        if (skippedCount > 0) {
            List<String> skippedNames = reservations.stream()
                    .filter(r -> r.getPickup() == null || r.getPickup().isBlank())
                    .map(Reservation::getCustomer)
                    .collect(Collectors.toList());
            log.warn("❗ {} rezervasyon pickup adresi eksik, optimize edilmeyecek. Müşteriler: {}", skippedCount, skippedNames);
        }

        if (validReservations.isEmpty()) {
            log.warn("❌ Geçerli pickup adresi olan hiç rezervasyon bulunamadı. Optimize işlemi iptal.");
            return;
        }

        // Şoför başlangıç noktaları
        List<double[]> driverStarts = new ArrayList<>();
        driverStarts.add(new double[]{36.876074, 31.086317}); // depo koordinatı

        // Pickup noktalarını coğrafi koordinatlara çevir
        List<double[]> pickups = new ArrayList<>();
        List<Integer> paxList = new ArrayList<>();

        // ❗ Pickup adresleri için cache
        Map<String, double[]> pickupCache = new HashMap<>();

        for (Reservation res : validReservations) {
            try {
                double[] latLng;
                if (pickupCache.containsKey(res.getPickup())) {
                    latLng = pickupCache.get(res.getPickup());
                    log.info("♻️ Cache'den alındı: {}", res.getPickup());
                } else {
                    latLng = geocodingService.geocode(res.getPickup());
                    pickupCache.put(res.getPickup(), latLng);
                    log.info("🧭 Geocode edildi: {}", res.getPickup());
                }

                if (latLng != null) {
                    pickups.add(latLng);
                    paxList.add(2); // Şimdilik her rezervasyon 2 kişi gibi
                } else {
                    log.warn("❗ Geocode sonucu null geldi: {}", res.getPickup());
                }
            } catch (Exception e) {
                log.warn("❗ Pickup geocode edilemedi, rezervasyon atlandı: {}", res.getPickup(), e);
            }
        }

        if (pickups.isEmpty()) {
            log.warn("❌ Hiç pickup noktası bulunamadı, optimizasyon iptal.");
            return;
        }

        // VRP çözümü yap
        Map<Integer, List<Integer>> solution = vrpService.solveVrp(driverStarts, pickups, paxList, driverStarts.size());

        if (solution.isEmpty()) {
            log.warn("❌ VRP çözümü boş döndü!");
            return;
        }

        // Çözümü logla
        for (Map.Entry<Integer, List<Integer>> entry : solution.entrySet()) {
            int vehicle = entry.getKey();
            List<Integer> route = entry.getValue();
            log.info("\uD83D\uDE98 Araç {} rotası: {}", vehicle, route);
        }

        log.info("✅ Rota optimizasyonu başarıyla tamamlandı.");
    }
}
