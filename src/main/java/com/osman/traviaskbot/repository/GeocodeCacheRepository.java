package com.osman.traviaskbot.repository;

import com.osman.traviaskbot.entity.GeocodeCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, Long> {
    GeocodeCache findByAddress(String address);
}
