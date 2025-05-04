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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "http://localhost:8080")
@RequiredArgsConstructor
@Slf4j
public class RouteController {

    /* ────────── ENUM & SABİTLER ────────── */
    public enum Region { KEMER, SIDE, OTHER }
    public static final List<String> DRIVER_ADDRS = List.of(
            "Mahmutlar, Sarıhasanlı Cd. 86, Alanya/Antalya",
            "Bahtılı, 3351 Sokak 4, Konyaaltı/Antalya",
            "1620 sokak 25/1, Muratpaşa/Antalya",
            "Kundu, Tesisler Cad. 454, Aksu/Antalya",
            "Kiriş, Sahil Cd. 15, Kemer/Antalya",
            "Şehit Er Hasan Yılmaz Cd. 20, Kemer/Antalya",
            "Kumköy, 07600 Manavgat/Antalya"
    );
    private static final String LAND_OF_LEGENDS = "36.876074,31.086317";
    private static final int    MAX_PAX          = 16;

    /* ────────── BAĞIMLILIKLAR ────────── */
    private final ReservationProcessor processor;
    private final RouteService routeService;
    private final VrpService   vrpService;

    /* lastStarts – mapsUrls için */
    private List<double[]> lastStarts = Collections.emptyList();

    /* ════════════════════════════════════════════════
       1)  /optimized   — tur & tarih bazında VRP
       ════════════════════════════════════════════════ */
    @GetMapping("/optimized")
    public Map<String, Map<String, List<String>>> optimizedRoutes(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after,
            @RequestParam(required = false) String[] tours      // ?tours=Land...,Suluada...
    ) {
        List<String> wantedTours = (tours == null || tours.length == 0)
                ? List.of("From Antalya, Alanya, Kemer: The Land of Legends Night Show",
                "Turkish Maldives: Suluada Day Trip + Lunch & Swim")
                : Arrays.asList(tours);

        Map<String, Map<String, List<String>>> all = new LinkedHashMap<>();

        for (String tour : wantedTours) {
            var dtos = processor.fetchDtos(after, tour);
            if (dtos.isEmpty()) continue;

            Map<String,List<String>> vrp = solveVrpForDtos(dtos);
            all.put(tour, vrp);
        }
        return all;
    }

    /* ════════════════════════════════════════════════
       2)  /optimized/mapsUrls
       ════════════════════════════════════════════════ */
    @GetMapping("/optimized/mapsUrls")
    public Map<String, Map<String,String>> mapsUrls(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after,
            @RequestParam(required = false) String[] tours
    ) {
        var routes = optimizedRoutes(after, tours);
        Map<String, Map<String,String>> urls = new LinkedHashMap<>();

        routes.forEach((tour, drivers) -> {
            Map<String,String> tourUrls = new LinkedHashMap<>();
            drivers.forEach((driver, picks) -> {

                int idx = Integer.parseInt(driver.replace("driver", "")) - 1;

                String origin      = encodeCoord(lastStarts.get(idx));
                String destination = URLEncoder.encode(LAND_OF_LEGENDS, StandardCharsets.UTF_8);

                String waypointStr = picks.stream().distinct()
                        .map(w -> URLEncoder.encode(w, StandardCharsets.UTF_8))
                        .collect(Collectors.joining("|"));

                String url = "https://www.google.com/maps/dir/?api=1" +
                        "&origin="      + origin +
                        "&destination=" + destination +
                        "&waypoints=optimize:true|" + waypointStr +
                        "&travelmode=driving";

                tourUrls.put(driver, url);
            });
            urls.put(tour, tourUrls);
        });
        return urls;
    }

    /* ════════════════════════════════════════════════
       3)  /optimized/schedules (driver‑pickup tablosu)
       ════════════════════════════════════════════════ */
    @GetMapping("/optimized/schedules")
    public Map<String, Map<String,List<String>>> schedules(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after,
            @RequestParam(required = false) String[] tours) {
        return optimizedRoutes(after, tours);
    }

    /* ─────────── HELPER: tek tur için VRP çözer ─────────── */
    private Map<String,List<String>> solveVrpForDtos(List<ReservationDto> dtos) {

        /* (a) Şoför hubları */
        List<double[]> driverStarts = new ArrayList<>();
        List<Boolean> isKemerDriver = new ArrayList<>();
        for (String addr : DRIVER_ADDRS) {
            try {
                driverStarts.add(routeService.toLatLng(addr));
                isKemerDriver.add(addr.toLowerCase().contains("kemer"));
            } catch (Exception e) {
                log.error("Driver geocode failed: {}", addr, e);
            }
        }
        int hubCount = driverStarts.size();

        /* (b) Pickup + pax + bölge kodları */
        List<double[]> pickups = new ArrayList<>();
        List<Integer>  paxList = new ArrayList<>();
        List<Integer>  regions = new ArrayList<>();

        for (ReservationDto d : dtos) {
            try {
                pickups.add(routeService.toLatLng(d.getPickup()));
                paxList.add(d.getAdults() + d.getChildren());
                regions.add(regionCode(d.getDistrict()));
            } catch (Exception ex) {
                log.warn("⛔ Geocode atlandı: {}", d.getPickup());
            }
        }
        if (pickups.isEmpty()) return Collections.emptyMap();

        /* (c) Araç havuzu → yolcu sayısına göre kopya oluştur */
        int totalPax = paxList.stream().mapToInt(Integer::intValue).sum();
        int minVeh   = Math.max(4, (int) Math.ceil((double) totalPax / MAX_PAX));

        List<double[]> starts = new ArrayList<>(driverStarts);
        int cloneIdx = 0;
        while (starts.size() < minVeh) {
            starts.add(driverStarts.get(cloneIdx % hubCount));
            isKemerDriver.add(isKemerDriver.get(cloneIdx % hubCount));
            cloneIdx++;
        }

        /* (d) VRP çöz */
        var sol = vrpService.solveVrp(
                starts, pickups, paxList, regions, isKemerDriver, starts.size());

        this.lastStarts = new ArrayList<>(starts);   // mapsUrls için

        /* (e) Index → pickup metni */
        int offset = starts.size();
        Map<String,List<String>> result = new LinkedHashMap<>();
        sol.forEach((v, nodes) -> {
            List<String> picks = new ArrayList<>();
            for (int idx : nodes)
                if (idx >= offset && idx < offset + pickups.size())
                    picks.add(dtos.get(idx - offset).getPickup());
            if (!picks.isEmpty()) result.put("driver" + (v + 1), picks);
        });
        return result;
    }

    private int regionCode(String dist) {
        if (List.of("Kemer","Beldibi","Çamyuva","Göynük").contains(dist)) return Region.KEMER.ordinal();
        if (List.of("Side","Sorgun","Evrenseki","Çolaklı","Kızılot","Kızılğaç","Manavgat").contains(dist))
            return Region.SIDE.ordinal();
        return Region.OTHER.ordinal();
    }

    private static String encodeCoord(double[] ll) {
        return URLEncoder.encode(ll[0] + "," + ll[1], StandardCharsets.UTF_8);
    }
}
