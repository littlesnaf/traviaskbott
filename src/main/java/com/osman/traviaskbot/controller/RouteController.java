package com.osman.traviaskbot.controller;

import com.google.maps.model.DirectionsResult;
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
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "http://localhost:8080")
@RequiredArgsConstructor
@Slf4j
public class RouteController {

    /* ───────────── Bölge tanımı ───────────── */
    public enum Region { KEMER, SIDE, OTHER }

    /* ───────────── Sabitler ───────────── */
    public static final List<String> DRIVER_ADDRS = List.of(
            // 0-1 → Alanya (Mahmutlar)
            "Mahmutlar, Sarıhasanlı Cd. no:86, 07450 Alanya/Antalya, Türkiye",
            // 2   → Konyaaltı
            "Bahtılı, 3351 Sokak No:4, 07070 Konyaaltı/Antalya, Türkiye",
            // 3   → Muratpaşa (şehir içi)
            "1620 sokak no 25 daire 1 Muratpaşa Antalya",
            // 4   → Kundu / Aksu
            "Kundu, Tesisler Caddesi No:454, 07112 Aksu/Antalya, Türkiye",
            // 5-7 → Kemer bölgesi
            "Kiriş, Sahil Cd. No:15, 07980 Kemer/Antalya, Türkiye",
            "Şehit Er Hasan Yılmaz Cad. No:20, 07000 Kemer/Antalya, Türkiye",
            //8   → Manavgat
            "Sorgun, 07600 Manavgat/Antalya, Türkiye"
    );
    private static final String LAND_OF_LEGENDS = "36.876074,31.086317";
    private static final int    MAX_PAX         = 16;

    /* ───────────── Bağımlılıklar ───────────── */
    private final ReservationProcessor processor;
    private final RouteService         routeService;
    private final VrpService           vrpService;

    /* optimizedRoutes() çağrısında oluşturulan başlangıç listesi
       – maps & schedules uç noktaları burada kullanacak */
    private List<double[]> lastStarts = Collections.emptyList();

    /* ═════════════════════════════════════════════════════════════
       1)  → Şoför-pickup eşlemesi
       ═════════════════════════════════════════════════════════════ */
    @GetMapping("/optimized")
    public Map<String,List<String>> optimizedRoutes(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after) {

        /* (a) Hub koordinatları + hangi araç Kemer’de? */
        List<double[]> driverStarts   = new ArrayList<>();
        List<Boolean>  isKemerDriver  = new ArrayList<>();
        for (String addr : DRIVER_ADDRS) {
            try {
                driverStarts.add(routeService.toLatLng(addr));
                isKemerDriver.add(addr.toLowerCase().contains("kemer"));
            } catch (Exception ex) {
                log.error("⛔ Şoför adresi geocode edilemedi → {}", addr, ex);
                throw new RuntimeException(ex);
            }
        }
        int hubCount = driverStarts.size();

        /* (b) Rezervasyon DTO’ları */
        List<ReservationDto> all = processor.fetchDtos(after);

        /* (c) Pickup listeleri */
        List<double[]>        pickupCoords = new ArrayList<>();
        List<ReservationDto>  good         = new ArrayList<>();
        List<Integer>         paxList      = new ArrayList<>();
        List<Integer>         regionCodes  = new ArrayList<>();

        for (ReservationDto d : all) {
            try {
                pickupCoords.add(routeService.toLatLng(d.getPickup()));
                good.add(d);
                paxList.add(d.getAdults() + d.getChildren());

                /* ➜ Bölge kodu */
                String dist = d.getDistrict();
                if (List.of("Kemer","Beldibi","Çamyuva","Göynük").contains(dist))
                    regionCodes.add(Region.KEMER.ordinal());
                else if (List.of("Side","Sorgun","Evrenseki","Çolaklı",
                        "Kızılot","Kızılğaç","Manavgat").contains(dist))
                    regionCodes.add(Region.SIDE.ordinal());
                else regionCodes.add(Region.OTHER.ordinal());

            } catch (Exception ex) {
                log.warn("⛔ Geocode başarısız → '{}'", d.getPickup());
            }
        }
        if (pickupCoords.isEmpty()) return Collections.emptyMap();

        /* (d) Araç sayısı ve start listesi */
        int totalPax    = paxList.stream().mapToInt(Integer::intValue).sum();
        int minVeh      = Math.max(4, (int) Math.ceil((double) totalPax / MAX_PAX));
        int vehicleUsed = Math.max(minVeh, 1);

        List<double[]> starts = new ArrayList<>(driverStarts);
        for (int i = 0; i < vehicleUsed - hubCount; i++) {
            int base = i % hubCount;
            starts.add(driverStarts.get(base));
            isKemerDriver.add(isKemerDriver.get(base));   // ← EKLENDİ
        }


        /* (e) VRP çöz */
        Map<Integer,List<Integer>> sol = vrpService.solveVrp(
                starts,
                pickupCoords,
                paxList,
                regionCodes,
                isKemerDriver,
                starts.size()
        );
        this.lastStarts = new ArrayList<>(starts);

        /* (f) Index -> pickup metni + çift adres filtresi */
        int offset = starts.size();
        Map<String,List<String>> result = new LinkedHashMap<>();
        sol.forEach((v,nodes) -> {
            Set<String> seen = new HashSet<>();
            List<String> picks = new ArrayList<>();
            for (int idx : nodes) {
                if (idx >= offset && idx < offset + pickupCoords.size()) {
                    String addr = good.get(idx - offset).getPickup();
                    if (seen.add(addr)) {      // yinelenen durak → atla
                        picks.add(addr);
                    }
                }
            }
            if (!picks.isEmpty()) result.put("driver" + (v + 1), picks);
        });
        return result;
    }

    /**
     * Google Maps URL’lerini döndürür.
     * Örn: /api/routes/optimized/mapsUrls?after=2025‑04‑01
     */
    @GetMapping("/optimized/mapsUrls")
    public Map<String,String> mapsUrls(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after) {

        // optimizedRoutes() önce çağrılmış olmalı,
        // lastStarts ve VRP çözümü bellekte duruyor.
        Map<String,List<String>> routes = optimizedRoutes(after);

        Map<String,String> urls = new LinkedHashMap<>();
        routes.forEach((driver,picks) -> {
            String origin      = URLEncoder.encode(lastStarts.get(
                    Integer.parseInt(driver.replace("driver",""))-1
            )[0] + "," +
                    lastStarts.get(
                            Integer.parseInt(driver.replace("driver",""))-1
                    )[1], StandardCharsets.UTF_8);
            String destination = URLEncoder.encode(RouteController.LAND_OF_LEGENDS,
                    StandardCharsets.UTF_8);
            String waypoints   = picks.stream()
                    .map(p -> URLEncoder.encode(p,
                            StandardCharsets.UTF_8))
                    .collect(Collectors.joining("|"));
            String url = String.format(
                    "https://www.google.com/maps/dir/?api=1&origin=%s&destination=%s"
                            + "&travelmode=driving&waypoints=%s", origin, destination, waypoints);
            urls.put(driver, url);
        });
        return urls;
    }

    /**
     * Sürücü başına sırayla pickup + drop listeleyen tablo.
     * Örn: /api/routes/optimized/schedules?after=2025‑04‑01
     */
    @GetMapping("/optimized/schedules")
    public Map<String,List<String>> schedules(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after) {

        // optimize edilmiş rotayı zaten hesapladıysanız bellekte var,
        // yoksa yeniden çağırabilir:
        return optimizedRoutes(after);
    }

}
