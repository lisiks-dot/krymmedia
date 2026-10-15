package ru.pacco.sourcing;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Импорт каталога поставщиков.
 *
 * @param userAgent      честный User-Agent со ссылкой на страницу о боте
 * @param delayMs        пауза между запросами к одному сайту — не нагружаем поставщика
 * @param maxItems       потолок товаров за одну загрузку
 * @param markupPercent  наценка по умолчанию к цене поставщика (закупка в рознице + обработка)
 * @param refreshCron    обновление цен и наличия уже опубликованных товаров
 */
@ConfigurationProperties(prefix = "pacco.sourcing")
public record SourcingProperties(String userAgent, long delayMs, int maxItems, BigDecimal markupPercent, String refreshCron) {

    public SourcingProperties {
        if (userAgent == null || userAgent.isBlank()) userAgent = "ilpacco-catalog-import/0.4 (+https://ilpacco.ru/legal#bot)";
        if (delayMs <= 0) delayMs = 3000;
        if (maxItems <= 0) maxItems = 200;
        if (markupPercent == null) markupPercent = BigDecimal.TEN;
    }
}
