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

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "http://localhost:8080")
@RequiredArgsConstructor
@Slf4j
public class RouteController {

    /* ───────────── Sabitler ───────────── */
    public static final List<String> DRIVER_ADDRS = List.of(
            "Mahmutlar, Sarıhasanlı Cd. no:86, 07450 Alanya/Antalya, Türkiye",
            "Mahmutlar, Sarıhasanlı Cd. no:86, 07450 Alanya/Antalya, Türkiye",
            "Bahtılı, 3351 Sokak No:4, 07070 Konyaaltı/Antalya, Türkiye",
            "1620 sokak no 25 daire 1 Muratpaşa Antalya",
            "Kundu, Tesisler Caddesi No:454, 07112 Aksu/Antalya, Türkiye",
            "Kiriş, Sahil Cd. No:15, 07980 Kemer/Antalya, Türkiye",
            "Kiriş, Sahil Cd. No:15, 07980 Kemer/Antalya, Türkiye",
            "Şehit Er Hasan Yılmaz Cad. No:20, 07000 Kemer/Antalya, Türkiye"
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
    public Map<String, List<String>> optimizedRoutes(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after) {

        /* (a) Hub koordinatları */
        List<double[]> driverStarts = new ArrayList<>();
        for (String addr : DRIVER_ADDRS) {
            try {
                driverStarts.add(routeService.toLatLng(addr));
            } catch (Exception ex) {
                log.error("⛔ Şoför adresi geocode edilemedi → {}", addr, ex);
                throw new RuntimeException(ex);
            }
        }
        int hubCount = driverStarts.size();

        /* (b) Rezervasyon DTO’ları */
        List<ReservationDto> all = processor.fetchDtos(after);

        /* (c) Geocode edilmiş pickup’lar */
        List<double[]> pickupCoords = new ArrayList<>();
        List<ReservationDto> good   = new ArrayList<>();
        List<Integer> paxList       = new ArrayList<>();
        for (ReservationDto d : all) {
            try {
                pickupCoords.add(routeService.toLatLng(d.getPickup()));
                good.add(d);
                paxList.add(d.getAdults() + d.getChildren());
            } catch (Exception ex) {
                log.warn("⛔ Geocode başarısız → '{}'", d.getPickup());
            }
        }
        if (pickupCoords.isEmpty()) return Collections.emptyMap();

        /* (d) Araç sayısı ve start listesi */
        int totalPax    = paxList.stream().mapToInt(Integer::intValue).sum();
        int minVeh      = Math.max(4, (int) Math.ceil((double) totalPax / MAX_PAX));
        int vehicleUsed = Math.max(minVeh, 1);

        List<double[]> starts = new ArrayList<>(driverStarts);          // önce tüm hub’lar
        for (int i = 0; i < vehicleUsed - hubCount; i++)                // gerekirse kopyala
            starts.add(driverStarts.get(i % hubCount));

        /* (e) VRP çöz – solveVrp lastStarts içine kopyalar */
        Map<Integer,List<Integer>> sol = vrpService.solveVrp(
                starts, pickupCoords, paxList, starts.size()
        );
        this.lastStarts = new ArrayList<>(starts);   // maps & schedules için sakla

        /* (f) Node → pickup metni */
        int offset = starts.size();
        Map<String,List<String>> result = new LinkedHashMap<>();
        sol.forEach((v, nodes) -> {
            List<String> picks = new ArrayList<>();
            for (int idx : nodes) {
                if (idx >= offset && idx < offset + pickupCoords.size())
                    picks.add(good.get(idx - offset).getPickup());
            }
            if (!picks.isEmpty())
                result.put("driver" + (v + 1), picks);
        });
        return result;
    }

    /* ═════════════════════════════════════════════════════════════
       2)  → Google Maps URL’leri
       ═════════════════════════════════════════════════════════════ */
    @GetMapping("/optimized/mapsUrls")
    public Map<String, Map<String,String>> optimizedMaps(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after) {

        Map<String,List<String>> routes = optimizedRoutes(after);
        Map<String,Map<String,String>> urls = new LinkedHashMap<>();

        int idx = 0;
        for (var e : routes.entrySet()) {
            List<String> picks  = e.getValue();
            String origin = DRIVER_ADDRS.get(idx % DRIVER_ADDRS.size());

            String goUrl  = buildMapsUrl(origin, LAND_OF_LEGENDS, picks);
            List<String> ret = new ArrayList<>(picks); Collections.reverse(ret);
            String retUrl = buildMapsUrl(LAND_OF_LEGENDS, origin, ret);

            urls.put(e.getKey(), Map.of("go", goUrl, "return", retUrl));
            idx++;
        }
        return urls;
    }

    /* ═════════════════════════════════════════════════════════════
       3)  → Varış-ayrılış saatleri (10 dk bekleme)
       ═════════════════════════════════════════════════════════════ */
    @GetMapping("/optimized/schedules")
    public Map<String, List<Map<String,String>>> optimizedSchedules(
            @RequestParam(defaultValue = "1970-01-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate after
    ) throws Exception {

        Map<String,List<String>> routes = optimizedRoutes(after);
        Map<String,List<Map<String,String>>> schedules = new LinkedHashMap<>();

        int idx = 0;
        for (var e : routes.entrySet()) {
            String origin = DRIVER_ADDRS.get(idx % DRIVER_ADDRS.size());
            List<String> picks = e.getValue();

            DirectionsResult dr = routeService.buildRoute(origin, picks, LAND_OF_LEGENDS);

            List<Map<String,String>> info = new ArrayList<>();
            LocalTime t = LocalTime.of(9,0);
            for (var leg : dr.routes[0].legs) {
                t = t.plusSeconds(leg.duration.inSeconds);
                Map<String,String> m = new LinkedHashMap<>();
                m.put("stop", leg.startAddress);
                m.put("arrival", t.toString());
                t = t.plusMinutes(10);
                m.put("departure", t.toString());
                info.add(m);
            }
            schedules.put(e.getKey(), info);
            idx++;
        }
        return schedules;
    }

    /* ───────────── Yardımcı ───────────── */
    private String buildMapsUrl(String origin, String destination, List<String> wps) {
        Function<String,String> enc = s -> URLEncoder.encode(s, StandardCharsets.UTF_8);
        String wp = wps.isEmpty() ? "" :
                "&waypoints=" + String.join("%7C", wps.stream().map(enc).toList());
        return "https://www.google.com/maps/dir/?api=1" +
                "&origin="      + enc.apply(origin) +
                "&destination=" + enc.apply(destination) +
                "&travelmode=driving" + wp;
    }
}
