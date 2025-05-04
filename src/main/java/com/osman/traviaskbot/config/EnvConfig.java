package com.osman.traviaskbot.config;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvConfig {
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    public static String getGoogleMapsApiKey() {
        return dotenv.get("GOOGLE_MAPS_API_KEY");
    }

    public static String getGmailUser() {
        return dotenv.get("GMAIL_USER");
    }

    public static String getGmailPassword() {
        return dotenv.get("GMAIL_PASSWORD");
    }
}
