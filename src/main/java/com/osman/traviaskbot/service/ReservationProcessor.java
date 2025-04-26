// src/main/java/com/osman/traviaskbot/service/ReservationProcessor.java
package com.osman.traviaskbot.service;

import com.osman.traviaskbot.dto.ReservationDto;
import com.osman.traviaskbot.entity.Reservation;
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
import java.util.stream.Collectors;
import com.osman.traviaskbot.entity.Route;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;



@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationProcessor {

    /* ─────────────── sabitler ─────────────── */
    private static final String HOST = "imap.gmail.com";
    private static final int MAX_EMAILS = 50; // tek seferde bakılacak maksimum mail

    /* ─────────────── bağımlılıklar ─────────────── */
    private final ReservationRepository reservationRepo;
    private final UnparsedMailRepository unparsedRepo;
    private final DistrictExtractor districtExtractor;
    private List<Route> vrpResults;

    @Value("${gmail.user}")
    private String gmailUser;
    @Value("${gmail.password}")
    private String gmailPass;

    /* ════════════════════════════════════════════════════════════════════════════════
       1) CRON / Scheduler tarafı – Mail’leri çek, çözümle, kaydet
       ════════════════════════════════════════════════════════════════════════════════ */
    public void processReservations() {

        int ok = 0, fail = 0;

        for (String body : fetchEmailBodies()) {
            try {
                Map<String, Object> data = parseEmail(body);
                if (data != null) {
                    saveReservation(data);
                    ok++;
                } else {
                    logUnparsed(body);
                    fail++;
                }
            } catch (Exception ex) { // tek mail patlasa da döngü devam etsin
                log.error("⛔ Mail parse hatası", ex);
                fail++;
            }
        }
        log.info("🔄 Parse bitti → başarılı {}, hatalı {}", ok, fail);
    }

    /* ════════════════════════════════════════════════════════════════════════════════
       2) Controller’lar için – İstenilen tarihten sonraki rezervasyonları DTO olarak döner
       ════════════════════════════════════════════════════════════════════════════════ */
    public List<ReservationDto> fetchDtos(LocalDate after) {

        log.debug("▶️ ReservationProcessor.fetchDtos after={} çağrıldı", after);

        return reservationRepo
                .findByDateGreaterThanEqualOrderByDateAscTimeAsc(after)
                .stream()
                /* Boş veya null pickup adreslerini **ELER** */
                .filter(dto ->
                        dto.getPickup() != null && !dto.getPickup().isBlank())
                .map(ReservationDto::of)
                .collect(Collectors.toList());
    }

    /* ════════════════════════════════════════════════════════════════════════════════
       VRP işlemleri ve sonuçları
       ════════════════════════════════════════════════════════════════════════════════ */
    public void processVrp() {
        log.info("🔄 VRP işlemi başlatıldı.");

        // VRP hesaplama metodu. Bu örnek bir algoritmadır.
        vrpResults = someVrpAlgorithm(); // VRP hesaplama işlemi

        log.info("🔄 VRP işlemi tamamlandı.");
    }

    public List<Route> getVrpResults() {
        return vrpResults;
    }

    /**
     * VRP hesaplama için basit bir örnek algoritma.
     * Gerçek VRP algoritmanız burada olacak.
     * @return Hesaplanmış rota listesi
     */
    private List<Route> someVrpAlgorithm() {
        // Örnek VRP sonuçları
        List<Route> routes = new ArrayList<>();
        routes.add(new Route("Route 1", 15.5)); // Örnek rota
        routes.add(new Route("Route 2", 20.0)); // Örnek rota
        return routes;
    }

    /* ════════════════════════════════════════════════════════════════════════════════
       --------------  A Ş A Ğ I S I   P R İ V A T E   Y A R D I M C I L A R  --------------
       ──────────────────────────────────────────────────────────────────────────────── */

    /** IMAP’ten gövdeleri çeker (yalnızca okunmamış & doğru gönderen) */
    private List<String> fetchEmailBodies() {

        List<String> bodies = new ArrayList<>();

        try {
            Session session = Session.getDefaultInstance(new Properties());
            Store store = session.getStore("imaps");
            store.connect(HOST, gmailUser, gmailPass);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            /* sadece “UNSEEN” mail’lere bak */
            Message[] msgs = inbox.search(
                    new FlagTerm(new Flags(Flags.Flag.SEEN), false)
            );

            /* son MAX_EMAILS adedi */
            for (int i = Math.max(0, msgs.length - MAX_EMAILS); i < msgs.length; i++) {

                if (!isValidSender(msgs[i])) continue;

                String body = extractBody(msgs[i]);
                if (body != null && !body.isBlank())
                    bodies.add(body);
            }
            inbox.close(false);
            store.close();

        } catch (Exception ex) {
            log.error("✉️ Mail okuma hatası", ex);
        }
        return bodies;
    }

    /** Gönderen doğru mu? ( getyourguide notification ) */
    private boolean isValidSender(Message m) throws MessagingException {

        Address[] from = m.getFrom();
        return from != null &&
                from[0].toString().toLowerCase(Locale.ROOT)
                        .contains("@notification.getyourguide.com");
    }

    /** Recursively extract plain-text body */
    private String extractBody(Part p) throws Exception {

        if (p.isMimeType("text/plain")) return p.getContent().toString();
        if (p.isMimeType("text/html")) return Jsoup.parse(p.getContent().toString()).text();

        if (p.isMimeType("multipart/*")) {
            MimeMultipart mp = (MimeMultipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = extractBody(mp.getBodyPart(i));
                if (s != null) return s;
            }
        }
        return null;
    }

    /** Mail içeriğini Regex’lerle parçala – başarılıysa Map döner */
    private Map<String, Object> parseEmail(String raw) {

        String text = Jsoup.parse(raw).text(); // tüm HTML etiketlerini temizle
        Map<String, Object> r = new HashMap<>();

        /* ------------ Referans ------------ */
        String ref = match(text,
                "Reference number:\\s*(GYG\\w+)",
                "Referans numarası:\\s*(GYG\\w+)"
        );
        if (ref == null) return null;
        r.put("reference", ref);

        /* ------------ Tarih & Saat ------------ */
        String[] dt = matchGroups(text,
                "Date:\\s*([A-Za-z]+ \\d{1,2}, \\d{4})\\s+(\\d{1,2}:\\d{2}\\s*[AP]M)",
                "Tarih:\\s*(\\d{1,2} [A-Za-zçğıöşüÇĞİÖŞÜ]+ \\d{4})\\s+(\\d{1,2}:\\d{2})"
        );
        if (dt == null) return null;

        DateTimeFormatter enDateFmt = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
        DateTimeFormatter trDateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("tr"));

        LocalDate date;
        try {
            date = LocalDate.parse(dt[0], enDateFmt);
        } catch (DateTimeParseException e) { // Türkçe tarih
            date = LocalDate.parse(dt[0], trDateFmt);
        }

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        LocalTime time = LocalTime.parse(dt[1].toUpperCase(Locale.ROOT), timeFmt);

        r.put("date", date);
        r.put("time", time);

        /* ------------ Diğer alanlar ------------ */
        r.put("adults", extractNum(text, "(\\d+)\\s*x\\s*(Adult|Yetişkin)"));
        r.put("children", extractNum(text, "(\\d+)\\s*x\\s*(Child|Çocuk)"));
        r.put("phone", nvl(match(text, "Phone:\\s*([+\\d\\s]+)")));

        String pickup = nvl(match(text,
                "Pickup location:\\s*([^\\r\\n]+?)(?:\\s*Open in Google Maps|\\n)",
                "Pickup location:\\s*([^\\r\\n]+)"
        ));
        r.put("pickup", pickup);

        r.put("customer", nvl(match(text,
                "Main customer:\\s*([^\r\n]+?)\\s*(?:Phone:|Language:|$)"
        )));

        r.put("status", "confirmed");

        /* ------------ İlçe tahmini ------------ */
        String district = districtExtractor.extract(pickup);
        r.put("district", district);

        log.debug("Parse OK: reference={} date={} time={} district={}", ref, date, time, district);
        return r;
    }

    /** DB’ye kaydet */
    private void saveReservation(Map<String, Object> d) {

        Reservation res = new Reservation();
        res.setReference((String) d.get("reference"));
        res.setStatus((String) d.get("status"));
        res.setDate((LocalDate) d.get("date"));
        res.setTime((LocalTime) d.get("time"));
        res.setAdults((Integer) d.get("adults"));
        res.setChildren((Integer) d.get("children"));
        res.setCustomer((String) d.get("customer"));
        res.setPhone((String) d.get("phone"));
        res.setPickup((String) d.get("pickup"));
        res.setDistrict((String) d.get("district"));

        reservationRepo.save(res);
    }

    /** Parse edilemeyen mail gövdesini sakla */
    private void logUnparsed(String body) {
        unparsedRepo.save(new UnparsedMail(null, body, Instant.now()));
    }

    /* ─────────────── küçük yardımcı regex metodları ─────────────── */

    private String match(String txt, String... patterns) {

        for (String p : patterns) {
            Matcher m = Pattern.compile(p,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ).matcher(txt);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    private String[] matchGroups(String txt, String... patterns) {

        for (String p : patterns) {
            Matcher m = Pattern.compile(p,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ).matcher(txt);
            if (m.find())
                return new String[]{m.group(1).trim(), m.group(2).trim()};
        }
        return null;
    }

    private int extractNum(String txt, String pattern) {

        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(txt);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private String nvl(String s) { // null-safe trim
        return (s == null) ? "" : s.trim();
    }




}
