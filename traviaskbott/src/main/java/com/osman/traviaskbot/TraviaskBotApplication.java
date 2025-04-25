package com.osman.traviaskbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling          // cron/fixedRate görevleri için
public class TraviaskBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraviaskBotApplication.class, args);
    }
}
