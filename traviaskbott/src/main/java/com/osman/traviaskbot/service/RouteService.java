// src/main/java/com/osman/traviaskbot/service/RouteService.java
package com.osman.traviaskbot.service;

import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ZeroResultsException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.TravelMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

    private final GeoApiContext geoApiContext;

    /* ------------------------------------------------------------
       1) Pickup → [lat,lng]   (metin ise geocode ederek)
       ------------------------------------------------------------ */
    public double[] toLatLng(String addr) throws Exception {
        // 1-a) "lat,lng" formatıysa hemen dön
        String[] p = addr.split(",");
        if (p.length == 2) {
            try {
                return new double[] {
                        Double.parseDouble(p[0].trim()),
                        Double.parseDouble(p[1].trim())
                };
            } catch (NumberFormatException ignore) { /* metinmiş */ }
        }

        // 1-b) Aksi hâlde Google Geocoding
        GeocodingResult[] res = GeocodingApi
                .geocode(geoApiContext, addr)
                .await();

        if (res.length == 0)
            throw new ZeroResultsException("Geocode bulunamadı: " + addr);

        return new double[] {
                res[0].geometry.location.lat,
                res[0].geometry.location.lng
        };
    }

    /* ------------------------------------------------------------
       2) Google Directions → rota
       ------------------------------------------------------------ */
    public DirectionsResult buildRoute(
            String origin,
            List<String> waypoints,
            String destination
    ) throws Exception {

        return DirectionsApi.newRequest(geoApiContext)
                .origin(origin)
                .destination(destination)
                .mode(TravelMode.DRIVING)
                .waypoints(waypoints.toArray(new String[0]))
                .optimizeWaypoints(true)
                .await();           // ZeroResultsException burada fırlayabilir
    }
}
