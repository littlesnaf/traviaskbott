// src/main/java/com/osman/traviaskbot/service/ReservationProcessor.java
package com.osman.traviaskbot.service;

import com.osman.traviaskbot.dto.ReservationDto;
import com.osman.traviaskbot.entity.ChangedMail;
import com.osman.traviaskbot.entity.CancelledMail;
import com.osman.traviaskbot.entity.Reservation;
import com.osman.traviaskbot.entity.UnparsedMail;
import com.osman.traviaskbot.repository.ChangedMailRepository;
import com.osman.traviaskbot.repository.CancelledMailRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationProcessor {

    private static final String HOST       = "imap.gmail.com";
    private static final int    MAX_EMAILS = 50;

    private final ReservationRepository    reservationRepo;
    private final UnparsedMailRepository   unparsedRepo;
    private final CancelledMailRepository  cancelledRepo;
    private final ChangedMailRepository    changedRepo;
    private final DistrictExtractor        districtExtractor;

    @Value("${gmail.user}")     private String gmailUser;
    @Value("${gmail.password}") private String gmailPass;

    public void processReservations() {
        int ok = 0, fail = 0;

        for (String body : fetchEmailBodies()) {
            try {
                Map<String, Object> data = parseEmail(body);
                if (data == null) {
                    logUnparsed(body);
                    fail++;
                    continue;
                }

                String status = (String) data.get("status");
                switch (status) {
                    case "confirmed":
                        saveReservation(data);
                        break;
                    case "changed":
                        handleChange(data, body);
                        break;
                    case "cancelled":
                        handleCancellation(data, body);
                        break;
                    default:
                        logUnparsed(body);
                        fail++;
                        continue;
                }
                ok++;
            } catch (Exception ex) {
                log.error("⛔ Mail işlem hatası", ex);
                fail++;
            }
        }
        log.info("🔄 Parse bitti → başarılı {}, hatalı {}", ok, fail);
    }

    public List<ReservationDto> fetchDtos(LocalDate after) {
        return reservationRepo
                .findByDateGreaterThanEqualOrderByDateAscTimeAsc(after)
                .stream()
                .filter(r -> "confirmed".equals(r.getStatus()))
                .filter(r -> r.getPickup() != null && !r.getPickup().isBlank())
                .map(ReservationDto::of)
                .collect(Collectors.toList());
    }

    private List<String> fetchEmailBodies() {
        List<String> bodies = new ArrayList<>();
        try {
            Session session = Session.getDefaultInstance(new Properties());
            Store   store   = session.getStore("imaps");
            store.connect(HOST, gmailUser, gmailPass);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Message[] msgs = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            for (int i = Math.max(0, msgs.length - MAX_EMAILS); i < msgs.length; i++) {
                Message m = msgs[i];
                if (!isValidSender(m)) continue;
                String body = extractBody(m);
                if (body != null && !body.isBlank()) {
                    bodies.add(body);
                }
            }

            inbox.close(false);
            store.close();
        } catch (Exception ex) {
            log.error("✉️ Mail okuma hatası", ex);
        }
        return bodies;
    }

    private boolean isValidSender(Message m) throws MessagingException {
        Address[] from = m.getFrom();
        return from != null &&
                from[0].toString().toLowerCase(Locale.ROOT)
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

    private Map<String,Object> parseEmail(String raw) {
        String text = Jsoup.parse(raw).text();
        Map<String,Object> r = new HashMap<>();

        String ref = match(text,
                // İngilizce confirmed/cancelled maillerdeki kalıp
                "Reference number[:\\s]+(GYG\\w+)",
                // Türkçe
                "Referans numaras[ıi][:\\s]+(GYG\\w+)",
                // İngilizce değişiklik e-postaları:
                "booking has changed[:\\s]+(GYG\\w+)",
                "has changed.*?(GYG\\w+)",
                // Türkçe iptal mailleri
                "Bir rezervasyon iptal edildi.*?(GYG\\w+)"
        );
        if (ref == null) return null;
        r.put("reference", ref);

        // ─── 2) Status ───────────────────────────────────────────────────
        String lower = text.toLowerCase(Locale.ROOT);
        boolean isCancelled = lower.contains("cancel") || lower.contains("iptal");
        boolean isChanged   = lower.contains("change") || lower.contains("değişti");
        r.put("status", isCancelled ? "cancelled" : isChanged ? "changed" : "confirmed");

        // ─── 3) Date & Time ──────────────────────────────────────────────
        String[] dt = matchGroups(text,
                "Date[:\\s]+([A-Za-z]+ \\d{1,2}, \\d{4})\\s*(?:at)?\\s*(\\d{1,2}:\\d{2}\\s*[AP]M)",
                "Tarih[:\\s]+([A-Za-z]+ \\d{1,2}, \\d{4})\\s*(\\d{1,2}:\\d{2}\\s*[AP]M)",
                "Tarih[:\\s]+(\\d{1,2} [A-Za-zçğıöşüÇĞİÖŞÜ]+ \\d{4})\\s*(?:at)?\\s*(\\d{1,2}:\\d{2})"
        );
        if (dt == null) return null;
        r.put("date", parseDate(dt[0]));
        r.put("time", parseTime(dt[1]));

        // ─── 4) Diğer alanlar ─────────────────────────────────────────────
        r.put("adults",   extractNum(text, "(\\d+)\\s*[x×]\\s*(Adult|Yetişkin)"));
        r.put("children", extractNum(text, "(\\d+)\\s*[x×]\\s*(Child|Çocuk)"));
        r.put("phone",    nvl(match(text, "Phone[:\\s]*([+\\d\\s-]+)")));

        String pickup = nvl(match(text,
                "Pickup location[:\\s]*([^\\r\\n]+?)(?:Open in Google Maps|$)",
                "Pickup location[:\\s]*([^\\r\\n]+)"
        ));
        r.put("pickup", pickup);
        r.put("customer", nvl(match(text,
                "Main customer[:\\s]*([^\\r\\n]+?)\\s*(?:Phone:|Language:|$)"
        )));

        // ─── 5) İlçe ───────────────────────────────────────────────────────
        r.put("district", districtExtractor.extract(pickup));
        return r;
    }

    private void saveReservation(Map<String,Object> d) {
        Reservation res = new Reservation();
        res.setReference((String)d.get("reference"));
        res.setStatus   ((String)d.get("status"));
        res.setDate     ((LocalDate)d.get("date"));
        res.setTime     ((LocalTime)d.get("time"));
        res.setAdults   ((Integer)d.get("adults"));
        res.setChildren ((Integer)d.get("children"));
        res.setCustomer ((String)d.get("customer"));
        res.setPhone    ((String)d.get("phone"));
        res.setPickup   ((String)d.get("pickup"));
        res.setDistrict ((String)d.get("district"));
        reservationRepo.save(res);
    }

    private void handleChange(Map<String,Object> d, String rawBody) {
        String ref = (String) d.get("reference");
        reservationRepo.findByReference(ref)
                .ifPresentOrElse(existing -> {
                    existing.setDate    ((LocalDate)d.get("date"));
                    existing.setTime    ((LocalTime)d.get("time"));
                    existing.setAdults  ((Integer)d.get("adults"));
                    existing.setChildren((Integer)d.get("children"));

                    String pickup = (String)d.get("pickup");
                    if (pickup != null && !pickup.isBlank()) {
                        existing.setPickup(pickup);
                        existing.setDistrict(districtExtractor.extract(pickup));
                    }

                    String customer = (String)d.get("customer");
                    if (customer != null && !customer.isBlank()) {
                        existing.setCustomer(customer);
                    }
                    String phone = (String)d.get("phone");
                    if (phone != null && !phone.isBlank()) {
                        existing.setPhone(phone);
                    }

                    existing.setStatus  ("changed");
                    reservationRepo.save(existing);
                }, () -> saveReservation(d));
        // değişiklik mailini ayrı tabloya logla
        changedRepo.save(new ChangedMail(null, rawBody, Instant.now()));
    }

    private void handleCancellation(Map<String,Object> d, String rawBody) {
        String ref = (String) d.get("reference");
        reservationRepo.findByReference(ref)
                .ifPresent(existing -> {
                    // gerçekten sil:
                    reservationRepo.delete(existing);
                });
        // isterseniz hala iptal mailini loglayın
        cancelledRepo.save(new CancelledMail(null, rawBody, Instant.now()));
    }

    private void logUnparsed(String body) {
        unparsedRepo.save(new UnparsedMail(null, body, Instant.now()));
    }

    private LocalDate parseDate(String raw) {
        DateTimeFormatter en = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
        DateTimeFormatter tr = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("tr"));
        try {
            return LocalDate.parse(raw, en);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(raw, tr);
        }
    }

    private LocalTime parseTime(String raw) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        return LocalTime.parse(raw.toUpperCase(Locale.ROOT), fmt);
    }

    private String match(String txt, String... patterns) {
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(txt);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    private String[] matchGroups(String txt, String... patterns) {
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(txt);
            if (m.find()) return new String[]{ m.group(1).trim(), m.group(2).trim() };
        }
        return null;
    }

    private int extractNum(String txt, String pattern) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(txt);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }
}
