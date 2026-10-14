package ru.pacco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ilpacco.ru (кодовое имя PACCO) — маркетплейс итальянских продавцов с доставкой посылками в Россию.
 * Модульный монолит: каждый пакет первого уровня — модуль Spring Modulith.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PaccoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaccoApplication.class, args);
    }
}
