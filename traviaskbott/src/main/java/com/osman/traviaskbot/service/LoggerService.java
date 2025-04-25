package com.osman.traviaskbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerService {

    private static final Logger logger = LoggerFactory.getLogger(LoggerService.class);

    // Hatalı adresleri loglamak için metod
    public void logFailedAddress(String address, Exception e) {
        logger.error("Geocoding failed for address: " + address, e);
    }
}
