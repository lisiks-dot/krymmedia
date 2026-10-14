package ru.pacco.pricing;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры цены «под ключ». Тариф = себестоимость + 25 %:
 * наземная 8,1 € за посылку + 5,6 €/кг, авиа 8,1 € + 11,3 €/кг, шаг веса 100 г.
 */
@ConfigurationProperties(prefix = "pacco.pricing")
public record PricingProperties(
        BigDecimal rubPerEur,
        BigDecimal fxBuffer,
        long dutyFreeEurCents,
        BigDecimal dutyRate,
        BigDecimal dutyMinEurPerKg,
        BigDecimal vatRate,
        boolean vatEnabled,
        long parcelFeeRub,
        int packagingGrams,
        Tariff ground,
        Tariff air) {

    public record Tariff(BigDecimal perParcelEur, BigDecimal perKgEur) {
    }

    public Tariff tariff(DeliveryMode mode) {
        return mode == DeliveryMode.AIR ? air : ground;
    }
}
