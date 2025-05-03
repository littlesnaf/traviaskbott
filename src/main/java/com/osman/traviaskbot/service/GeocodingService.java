package com.osman.traviaskbot.service;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ZeroResultsException;
import com.google.maps.model.GeocodingResult;
import com.osman.traviaskbot.exception.AddressValidationException;
import com.osman.traviaskbot.util.AddressValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor          // << GeoApiContext artık DI ile geliyor
@Slf4j
public class GeocodingService {

    private final GeoApiContext context;           // <‑‑ @Configuration’da tanımlı
    private final Set<String> processed = new HashSet<>();

    public double[] geocode(String raw) {

        String address = normalize(raw);

        /* cache & validasyon */
        if (!AddressValidator.isValid(address) || processed.contains(address)) {
            log.warn("❗ Geocode atlandı: {}", address);
            throw new AddressValidationException("Invalid or duplicate address: " + address);
        }

        try {
            GeocodingResult[] res = GeocodingApi.geocode(context, address).await();
            if (res.length == 0) throw new ZeroResultsException("No geocode: " + address);

            processed.add(address);
            return new double[]{
                    res[0].geometry.location.lat,
                    res[0].geometry.location.lng
            };
        } catch (Exception e) {
            log.error("❌ Geocode error: {}", address, e);
            throw new RuntimeException(e);
        }
    }

    /* yardımcı */
    private String normalize(String a) {
        if (a == null) return "";
        return a.trim()
                .replaceAll("\\s+,", ",")
                .replaceAll(",(\\S)", ", $1")
                .replaceAll("\\s{2,}", " ");
    }
}
