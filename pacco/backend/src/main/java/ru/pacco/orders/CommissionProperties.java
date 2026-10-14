package ru.pacco.orders;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import ru.pacco.shared.Category;

/** Комиссия площадки по категориям (бизнес-план: 18 % одежда и обувь, 15 % косметика, 12 % дом и еда). */
@ConfigurationProperties(prefix = "pacco.commission")
public record CommissionProperties(Map<Category, BigDecimal> rates) {

    public BigDecimal rate(Category category) {
        return rates == null ? BigDecimal.ZERO : rates.getOrDefault(category, BigDecimal.ZERO);
    }
}
