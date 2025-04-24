// src/main/java/com/osman/traviaskbot/controller/RouteController.java
package com.osman.traviaskbot.controller;

import com.osman.traviaskbot.dto.ReservationDto;
import com.osman.traviaskbot.service.ReservationProcessor;
import com.osman.traviaskbot.service.RouteService;
import com.osman.traviaskbot.service.VrpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "http://localhost:8080")
@RequiredArgsConstructor
@Slf4j
public class RouteController {

    /* ---------- sabitler ---------- */
    private static final List<String> DRIVER_ADDRS = List.of(
            "Mahmutlar, Sarıhasanlı Cd. no:86, 07450 Alanya/Antalya, Türkiye",
            "Mahmutlar, Sarıhasanlı Cd. no:86, 07450 Alanya/Antalya, Türkiye",
            "Bahtılı, 3351 Sokak No:4, 07070 Konyaaltı/Antalya, Türkiye",
            "1620 sokak no 25 daire 1 Muratpaşa Antalya",
            "Kundu, Tesisler Caddesi No:454, 07112 Aksu/Antalya, Türkiye",
            "Kiriş, Sahil Cd. No:15, 07980 Kemer/Antalya, Türkiye",
            "Kiriş, Sahil Cd. No:15, 07980 Kemer/Antalya, Türkiye",
            "Şehit Er Hasan Yılmaz Cad. No:20, 07000 Kemer/Antalya, Türkiye"
    );
    private static final String LAND_OF_LEGENDS = "36.876074,31.086317";   // bitiş
    private static final int    MAX_PAX         = 16;                      // araç kapasitesi

    /* ---------- servisler ---------- */
    private final ReservationProcessor processor;
    private final RouteService         routeService;
    private final VrpService           vrpService;

    /* ======================================================
       1) optimizasyon → pickup listeleri
       ====================================================== */
    @GetMapping("/optimized")
    public Map<String,List<String>> optimizedRoutes(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after) {

        /* --- (a) şoför konumlarını geocode et --- */
        List<double[]> driverStarts = new ArrayList<>();
        for (String addr : DRIVER_ADDRS) {
            try {
                driverStarts.add(routeService.toLatLng(addr));
            } catch (Exception ex) {
                log.error("⛔ Şoför adresi geocode edilemedi → {}", addr, ex);
                throw new RuntimeException("Driver address geocode failed", ex);
            }
        }
        int driverCount = driverStarts.size();

        /* --- (b) rezervasyonları çek --- */
        List<ReservationDto> allDtos = processor.fetchDtos(after);

        /* --- (c) sadece geocode’u başarılı olanları al --- */
        List<double[]>       pickupCoords = new ArrayList<>();
        List<ReservationDto> goodDtos     = new ArrayList<>();
        List<Integer>        paxList      = new ArrayList<>();

        for (ReservationDto d : allDtos) {
            try {
                pickupCoords.add(routeService.toLatLng(d.getPickup()));
                goodDtos.add(d);
                paxList.add(d.getAdults() + d.getChildren());
            } catch (Exception ex) {
                log.warn("⛔ Geocode başarısız → '{}'", d.getPickup());
            }
        }
        if (pickupCoords.isEmpty()) return Collections.emptyMap();

        /* --- (d) kapasiteye göre araç sayısı --- */
        int totalPax     = paxList.stream().mapToInt(Integer::intValue).sum();
        int minVehicles = Math.max(4, (int) Math.ceil((double) totalPax / MAX_PAX));
        int vehicleUsed  = Math.min(driverCount, Math.max(minVehicles, 1));

        /* --- (e) VRP çöz --- */
        Map<Integer,List<Integer>> sol = vrpService.solveVrp(
                driverStarts.subList(0, vehicleUsed),
                pickupCoords,
                paxList,
                vehicleUsed);

        /* --- (f) index → adres --- */
        int offset = vehicleUsed;  // şoför düğümleri 0..offset-1
        Map<String,List<String>> res = new LinkedHashMap<>();
        sol.forEach((veh, nodes) -> {
            List<String> picks = nodes.stream()
                    .filter(i -> i >= offset && i < offset + pickupCoords.size())
                    .map(i -> goodDtos.get(i - offset).getPickup())
                    .toList();
            res.put("driver" + (veh + 1), picks);
        });
        return res;
    }

    /* ======================================================
       2) Google-Maps link’leri
       ====================================================== */
    @GetMapping("/optimized/mapsUrls")
    public Map<String,String> optimizedMaps(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after) {

        Map<String,List<String>> routeMap = optimizedRoutes(after);
        Map<String,String> urls = new LinkedHashMap<>();

        routeMap.forEach((drv,picks) -> {
            String origin = DRIVER_ADDRS.get(Integer.parseInt(drv.replace("driver",""))-1);
            urls.put(drv, buildMapsUrl(origin, LAND_OF_LEGENDS, picks));
        });
        return urls;
    }

    /* ---------- yardımcı ---------- */
    private String buildMapsUrl(String origin, String destination, List<String> waypoints) {
        String base = "https://www.google.com/maps/dir/?api=1";
        Function<String,String> enc = s -> URLEncoder.encode(s, StandardCharsets.UTF_8);

        return base +
                "&origin="      + enc.apply(origin) +
                "&destination=" + enc.apply(destination) +
                "&travelmode=driving" +
                (waypoints.isEmpty() ? "" :
                        "&waypoints=" +
                                String.join("%7C",
                                        waypoints.stream().map(enc).toList()));
    }
}
