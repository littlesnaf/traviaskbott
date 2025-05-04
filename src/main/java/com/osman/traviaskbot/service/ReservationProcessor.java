package com.osman.traviaskbot.service;

import com.osman.traviaskbot.controller.RouteController;
import com.osman.traviaskbot.dto.ReservationDto;
import com.osman.traviaskbot.entity.Reservation;
import com.osman.traviaskbot.entity.Route;
import com.osman.traviaskbot.entity.UnparsedMail;
import com.osman.traviaskbot.repository.ReservationRepository;
import com.osman.traviaskbot.repository.UnparsedMailRepository;
import com.osman.traviaskbot.util.DistrictExtractor;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationProcessor {

    private static final String HOST       = "imap.gmail.com";
    private static final int    MAX_EMAILS = 5000;

    private final ReservationRepository reservationRepo;
    private final UnparsedMailRepository unparsedRepo;
    private final DistrictExtractor      districtExtractor;
    private final RouteService           routeService;
    private final VrpService             vrpService;

    @Value("${gmail.user}")     private String gmailUser;
    @Value("${gmail.password}") private String gmailPass;

    public void processReservations() {
        int ok = 0, fail = 0;
        for (String body : fetchEmailBodies()) {
            try {
                Map<String,Object> data = parseEmail(body);
                if (data == null) {
                    logUnparsed(body);
                    fail++;
                } else {
                    saveReservation(data);
                    ok++;
                }
            } catch (Exception ex) {
                log.error("⛔ Mail parse exception", ex);
                fail++;
            }
        }
        log.info("🔄 Mail tarama tamam → ok={} fail={}", ok, fail);
    }

    public List<ReservationDto> fetchDtos(LocalDate after) {
        return fetchDtos(after, null);
    }

    public List<ReservationDto> fetchDtos(LocalDate after, String tourFilter) {
        var stream = (tourFilter == null || tourFilter.isBlank())
                ? reservationRepo.findByDateGreaterThanEqualOrderByDateAscTimeAsc(after).stream()
                : reservationRepo.findByDateGreaterThanEqualAndTourOrderByDateAscTimeAsc(after, tourFilter).stream();

        return stream
                .filter(r -> r.getPickup() != null && !r.getPickup().isBlank())
                .map(ReservationDto::of)
                .toList();
    }

    public List<Route> getVrpResults(LocalDate after) {
        List<ReservationDto> dtos = fetchDtos(after);
        if (dtos.isEmpty()) return Collections.emptyList();

        List<double[]> hubs       = new ArrayList<>();
        List<Boolean>  kemerFlags = new ArrayList<>();
        for (String addr : RouteController.DRIVER_ADDRS) {
            try {
                hubs.add(routeService.toLatLng(addr));
                kemerFlags.add(addr.toLowerCase().contains("kemer"));
            } catch (Exception e) {
                log.error("Hub geocode error: {}", addr, e);
            }
        }

        List<double[]> pickups = new ArrayList<>();
        List<Integer>  pax     = new ArrayList<>();
        List<Integer>  regions = new ArrayList<>();
        for (ReservationDto d : dtos) {
            try {
                pickups.add(routeService.toLatLng(d.getPickup()));
                pax.add(d.getAdults() + d.getChildren());
                regions.add(regionCode(d.getDistrict()));
            } catch (Exception ex) {
                log.warn("⛔ Pickup geocode atlandı: {}", d.getPickup());
            }
        }
        if (pickups.isEmpty()) return Collections.emptyList();

        Map<Integer,List<Integer>> sol = vrpService.solveVrp(
                hubs, pickups, pax, regions, kemerFlags, hubs.size()
        );

        List<Route> routes = new ArrayList<>();
        sol.forEach((veh, nodes) ->
                routes.add(new Route("driver" + (veh + 1), nodes.size() - 1))
        );
        return routes;
    }

    private List<String> fetchEmailBodies() {
        List<String> bodies = new ArrayList<>();
        try (Store store = Session.getDefaultInstance(new Properties()).getStore("imaps")) {
            store.connect(HOST, gmailUser, gmailPass);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] msgs = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            for (int i = Math.max(0, msgs.length - MAX_EMAILS); i < msgs.length; i++) {
                Message msg = msgs[i];
                if (!isValidSender(msg)) continue;
                String body = extractBody(msg);
                if (body != null && !body.isBlank()) {
                    bodies.add(body);
                    // 3️⃣ Mark as read
                    msg.setFlag(Flags.Flag.SEEN, true);
                }


            }
            inbox.close(false);
        } catch (Exception ex) {
            log.error("✉️ IMAP okuma hatası", ex);
        }
        return bodies;
    }

    private boolean isValidSender(Message m) throws MessagingException {
        Address[] from = m.getFrom();
        return from != null && from[0].toString().toLowerCase(Locale.ROOT)
                .contains("@notification.getyourguide.com");
    }

    private String extractBody(Part p) throws Exception {
        if (p.isMimeType("text/plain")) return p.getContent().toString();
        if (p.isMimeType("text/html"))  return Jsoup.parse(p.getContent().toString()).text();
        if (p.isMimeType("multipart/*")) {
            MimeMultipart mp = (MimeMultipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = extractBody(mp.getBodyPart(i));
                if (s != null) return s;
            }
        }
        return null;
    }

    private Map<String,Object> parseEmail(String rawHtml) {
        String text = Jsoup.parse(rawHtml).text();
        Map<String,Object> r = new HashMap<>();

        String ref = match(text,"Reference number:\\s*(GYG\\w+)");
        if (ref == null) return null;
        r.put("reference", ref);

        String[] dt = matchGroups(text,
                "Date:\\s*([A-Za-z]+ \\d{1,2}, \\d{4})\\s+(\\d{1,2}:\\d{2}\\s*[AP]M)",
                "Tarih:\\s*(\\d{1,2} [A-Za-zçğıöşüÇĞİÖŞÜ]+ \\d{4})\\s+(\\d{1,2}:\\d{2})"
        );
        if (dt == null) return null;

        LocalDate date;
        try {
            date = LocalDate.parse(dt[0],
                    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
        } catch (DateTimeParseException e) {
            date = LocalDate.parse(dt[0],
                    DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("tr")));
        }
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        LocalTime time = LocalTime.parse(dt[1].toUpperCase(Locale.ROOT), tf);
        r.put("date", date);
        r.put("time", time);

        // —— Sadece Suluada veya Land of Legends tespit et
        String tourLine = nvl(match(text,"offer has been booked:\\s*([^\\r\\n]+)"));
        String lower = tourLine.toLowerCase(Locale.ROOT);
        String tour;
        if (lower.contains("suluada")) {
            tour = "Suluada";
        } else if (lower.contains("legends")) {
            tour = "Land of Legends";
        } else {
            tour = "";
        }
        r.put("tour", tour);

        // opsiyon ismine artık gerek yoksa boş bırakabilirsiniz
        r.put("option", "");

        r.put("adults",   extractNum(text,"(\\d+)\\s*x\\s*(Adult|Yetişkin)"));
        r.put("children", extractNum(text,"(\\d+)\\s*x\\s*(Child|Çocuk)"));

        r.put("phone",    nvl(match(text,"Phone:\\s*([+\\d\\s]+)")));
        String pickup = nvl(match(text,
                "Pickup location:\\s*([^\\r\\n]+?)(?:\\s*Open in Google Maps|\\n)"));
        r.put("pickup",   pickup);
        r.put("customer", nvl(match(text,
                "Main customer:\\s*([^\r\n]+?)\\s*(?:Phone:|Language:|$)")));
        r.put("status",   "confirmed");
        r.put("district", districtExtractor.extract(pickup));

        return r;
    }

    private void saveReservation(Map<String,Object> d) {
        String ref = (String) d.get("reference");
        if (reservationRepo.existsByReference(ref)) {
            log.warn("❗ Çift rezervasyon atlandı: {}", ref);
            return;
        }
        Reservation r = new Reservation();
        r.setReference(ref);
        r.setStatus((String) d.get("status"));
        r.setTour((String) d.get("tour"));
        r.setOptionName("");
        r.setDate((LocalDate) d.get("date"));
        r.setTime((LocalTime) d.get("time"));
        r.setAdults((Integer) d.get("adults"));
        r.setChildren((Integer) d.get("children"));
        r.setCustomer((String) d.get("customer"));
        r.setPhone((String) d.get("phone"));
        r.setPickup((String) d.get("pickup"));
        r.setDistrict((String) d.get("district"));
        reservationRepo.save(r);
    }

    private void logUnparsed(String body) {
        unparsedRepo.save(new UnparsedMail(null, body, Instant.now()));
    }

    private String match(String txt, String... patterns) {
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(txt);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    private String[] matchGroups(String txt, String... patterns) {
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(txt);
            if (m.find()) return new String[]{m.group(1).trim(), m.group(2).trim()};
        }
        return null;
    }

    private int extractNum(String txt, String pattern) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(txt);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private String nvl(String s) { return s == null ? "" : s.trim(); }

    private int regionCode(String dist) {
        if (List.of("Kemer","Beldibi","Çamyuva","Göynük").contains(dist))
            return RouteController.Region.KEMER.ordinal();
        if (List.of("Side","Sorgun","Evrenseki","Çolaklı","Kızılot","Kızılğaç","Manavgat")
                .contains(dist))
            return RouteController.Region.SIDE.ordinal();
        return RouteController.Region.OTHER.ordinal();
    }
}
