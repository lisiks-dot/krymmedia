package ru.pacco.pricing;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Цена заказа «под ключ». Заказ всегда идёт одной посылкой.
 * Суммы в евро — в центах, суммы в рублях — целые рубли, округлённые вверх до 10 ₽.
 */
public record Quote(
        DeliveryMode mode,
        long goodsEurCents,
        int weightGrams,
        long deliveryEurCents,
        long dutyEurCents,
        long vatEurCents,
        long goodsRub,
        long deliveryRub,
        long dutyRub,
        long vatRub,
        long totalRub,
        boolean dutyFree,
        boolean foodOnly,
        Long removalHintProductId,
        Map<DeliveryMode, Long> deliveryRubByMode,
        BigDecimal rubPerEur) {
}
