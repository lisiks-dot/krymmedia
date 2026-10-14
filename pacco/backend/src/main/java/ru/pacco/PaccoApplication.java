package ru.pacco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * PACCO — маркетплейс итальянских продавцов с доставкой посылками в Россию.
 * Модульный монолит: каждый пакет первого уровня — модуль Spring Modulith.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaccoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaccoApplication.class, args);
    }
}
