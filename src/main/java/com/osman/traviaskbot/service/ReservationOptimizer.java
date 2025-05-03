package com.osman.traviaskbot.service;

import com.osman.traviaskbot.controller.RouteController.Region;
import com.osman.traviaskbot.entity.Reservation;
import com.osman.traviaskbot.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationOptimizer {

    private final ReservationRepository reservationRepository;
    private final GeocodingService geocodingService;
    private final VrpService vrpService;

    /** Depo koordinatı (Land of Legends) */
    private static final double[] DEPOT = {36.876074, 31.086317};

    public void optimize(List<Reservation> reservations) {

        /* 1) pickup’ı dolu rezervasyonlar */
        List<Reservation> valid = reservations.stream()
                .filter(r -> r.getPickup() != null && !r.getPickup().isBlank())
                .toList();
        if (valid.isEmpty()) {
            log.warn("❌ Optimize – pickup adresi yok.");
            return;
        }

        /* 2) Sürücü başlangıç listesi (tek araç = depo) */
        List<double[]> driverStarts = List.of(DEPOT);
        List<Boolean>  kemerFlags   = List.of(false);   // tek araç Kemer değil
        int numVehicles = driverStarts.size();

        /* 3) Pickup koordinatları & yolcu & bölge kodu */
        Map<String,double[]> cache = new HashMap<>();
        List<double[]> pickups = new ArrayList<>();
        List<Integer>  paxList = new ArrayList<>();
        List<Integer>  regions = new ArrayList<>();

        for (Reservation r : valid) {
            try {
                double[] ll = cache.computeIfAbsent(
                        r.getPickup(),
                        k -> geocodingService.geocode(k)
                );
                pickups.add(ll);
                paxList.add(r.getAdults() + r.getChildren());

                /* varsayılan OTHER – dilerseniz ilçe kontrolü ekleyin */
                regions.add(Region.OTHER.ordinal());

            } catch (Exception ex) {
                log.warn("❗ Geocode atlandı: {}", r.getPickup(), ex);
            }
        }
        if (pickups.isEmpty()) {
            log.warn("❌ Optimize – pickup listesi boş.");
            return;
        }

        /* 4) VRP çözümü */
        Map<Integer,List<Integer>> sol = vrpService.solveVrp(
                driverStarts,
                pickups,
                paxList,
                regions,
                kemerFlags,
                numVehicles
        );
        if (sol.isEmpty()) {
            log.warn("❌ Optimize – çözüm yok.");
            return;
        }

        /* 5) Logla */
        sol.forEach((v,route) ->
                log.info("🚐 Araç {} → rota düğümleri {}", v, route));
        log.info("✅ Optimize tamam.");
    }
}
