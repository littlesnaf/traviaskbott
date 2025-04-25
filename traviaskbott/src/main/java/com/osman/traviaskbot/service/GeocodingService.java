package com.osman.traviaskbot.service;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.osman.traviaskbot.util.AddressValidator;
import com.osman.traviaskbot.exception.AddressValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class GeocodingService {

    private final GeoApiContext context;
    private final Set<String> processedAddresses = new HashSet<>(); // Adresleri izlemek için set

    public GeocodingService() {
        this.context = new GeoApiContext.Builder()
                .apiKey(System.getProperty("google.maps.api-key"))
                .build();
    }

    /**
     * Adres bilgisinden [latitude, longitude] döner.
     */
    public double[] geocode(String address) {
        address = normalizeAddress(address);  // Adresi normalize ediyoruz

        // Adresin daha önce işlenip işlenmediğini kontrol et
        if (processedAddresses.contains(address)) {
            log.warn("❗ Adres daha önce işlendi: {}", address);
            return null; // Daha önce işlenmiş adresi geç
        }

        try {
            // Adres geçerli mi kontrol et
            if (!AddressValidator.isValid(address)) {
                log.warn("❗ Geocode iptal edildi. Geçersiz adres: {}", address);
                throw new AddressValidationException("Geocode iptal: Geçersiz adres -> " + address);
            }

            GeocodingResult[] results = GeocodingApi.geocode(context, address).await();
            if (results.length == 0) {
                log.warn("❗ Adres bulunamadı: {}", address);
                throw new RuntimeException("Adres bulunamadı: " + address);
            }

            // Geocoding sonuçlarını işleyin
            double lat = results[0].geometry.location.lat;
            double lng = results[0].geometry.location.lng;
            log.info("📍 Geocode başarılı: {} -> {}, {}", address, lat, lng);

            // İşlem sonrası adresi kaydedin
            processedAddresses.add(address);

            return new double[]{lat, lng};

        } catch (Exception e) {
            log.error("❌ Geocode hatası: {}", address, e);
            throw new RuntimeException("Geocode hatası: " + address, e);
        }
    }

    /**
     * Adresi normalize eder: boşlukları düzeltir, gereksiz karakterleri temizler.
     */
    private String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        // Baş ve sondaki boşlukları temizle
        String normalized = address.trim();
        // Virgülden önce boşluk varsa kaldır
        normalized = normalized.replaceAll("\\s+,", ",");
        // Virgülden sonra boşluk yoksa boşluk ekle
        normalized = normalized.replaceAll(",(\\S)", ", $1");
        // Birden fazla boşluğu teke indir
        normalized = normalized.replaceAll("\\s{2,}", " ");
        return normalized;
    }
}
