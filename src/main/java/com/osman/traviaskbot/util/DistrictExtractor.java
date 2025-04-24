// src/main/java/com/osman/traviaskbot/util/DistrictExtractor.java
package com.osman.traviaskbot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;

@Component
@Slf4j
public class DistrictExtractor {

    // Antalya'daki tüm ilçe isimleri:
    private static final List<String> DISTRICTS = List.of(
            "Tekirova","Çamyuva","Kemer","Göynük","Beldibi","Konyaaltı",
            "Kaleiçi - Muratpaşa","Lara","Kundu","Aksu","Kadriye","Belek",
            "Boğazkent","Serik","Gündoğdu","Çolaklı","Evrenseki","Kumköy",
            "Side","Sorgun","Manavgat","Muratpasa","Kızılot","Kızılğaç","Okurcalar",
            "İncekum","Avsallar","Türkler","Payallar","Konaklı","Alanya",
            "Oba","Tosmur","Kestel","Mahmutlar"
    );

    /**
     * @param rawAddress e‑postadan çektiğimiz "pickup" adresi
     * @return Bulabildiyse ilçe adı, bulamadıysa "UNKNOWN"
     */
    public String extract(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return "UNKNOWN";
        }

        // 1) Normalize & aksanları ayıkla
        String norm = Normalizer
                .normalize(rawAddress, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");        // tüm diakritikleri kaldır

        String lower = norm.toLowerCase();

        // 2) Her ilçeyi normalize edip basit contains ile kontrol et
        for (String district : DISTRICTS) {
            String dNorm = Normalizer
                    .normalize(district, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase();

            if (lower.contains(dNorm)) {
                return district;
            }
        }

        log.warn("❓ İlçe eşleşmedi → {}", rawAddress);
        return "UNKNOWN";
    }
}
