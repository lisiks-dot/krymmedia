package ru.pacco.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.pacco.shared.Category;
import ru.pacco.shared.ProductSnapshot;

/** Числа совпадают с прототипом витрины и с моделью бизнес-плана. */
class PricingServiceTest {

    private final PricingService pricing = new PricingService(new PricingProperties(
            new BigDecimal("113.3"), new BigDecimal("0.015"), 20000, new BigDecimal("0.05"), new BigDecimal("1.0"),
            new BigDecimal("0.22"), false, 100, 200,
            new PricingProperties.Tariff(new BigDecimal("8.1"), new BigDecimal("5.6")),
            new PricingProperties.Tariff(new BigDecimal("8.1"), new BigDecimal("11.3"))));

    private static final ProductSnapshot LOAFERS = product(1, Category.SHOES, 15800, 1000);
    private static final ProductSnapshot BOOTS = product(2, Category.SHOES, 21400, 1200);
    private static final ProductSnapshot OIL = product(10, Category.FOOD, 2100, 1300);
    private static final ProductSnapshot COFFEE = product(12, Category.FOOD, 2700, 1100);

    @Test
    void rubIncludesBufferAndRoundsUpToTenRoubles() {
        assertThat(pricing.rub(15800)).isEqualTo(18170);
        assertThat(pricing.rub(2100)).isEqualTo(2420);
        assertThat(pricing.rub(0)).isZero();
    }

    @Test
    void parcelUnderThresholdHasNoDuty() {
        Quote q = pricing.quote(List.of(new QuoteLine(LOAFERS, 1), new QuoteLine(OIL, 1)), DeliveryMode.GROUND);

        assertThat(q.goodsEurCents()).isEqualTo(17900);
        assertThat(q.weightGrams()).isEqualTo(2500);
        assertThat(q.deliveryEurCents()).isEqualTo(2210);   // 8,1 € + 5,6 €/кг × 2,5 кг
        assertThat(q.goodsRub()).isEqualTo(20590);
        assertThat(q.deliveryRub()).isEqualTo(2550);
        assertThat(q.dutyRub()).isZero();
        assertThat(q.totalRub()).isEqualTo(23140);
        assertThat(q.dutyFree()).isTrue();
        assertThat(q.removalHintProductId()).isNull();
        assertThat(q.deliveryRubByMode()).containsEntry(DeliveryMode.AIR, 4190L);
    }

    @Test
    void parcelOverThresholdPaysFivePercentOfWholeValueAndGetsRemovalHint() {
        Quote q = pricing.quote(List.of(new QuoteLine(LOAFERS, 1), new QuoteLine(OIL, 1), new QuoteLine(BOOTS, 1)),
                DeliveryMode.GROUND);

        assertThat(q.goodsEurCents()).isEqualTo(39300);
        assertThat(q.dutyEurCents()).isEqualTo(1965);
        assertThat(q.dutyRub()).isEqualTo(2260);
        assertThat(q.deliveryRub()).isEqualTo(3320);
        assertThat(q.totalRub()).isEqualTo(50780);
        assertThat(q.removalHintProductId()).isEqualTo(2L);
    }

    @Test
    void foodOnlyBasketIsFlagged() {
        Quote q = pricing.quote(List.of(new QuoteLine(OIL, 1), new QuoteLine(COFFEE, 2)), DeliveryMode.GROUND);
        assertThat(q.foodOnly()).isTrue();

        Quote mixed = pricing.quote(List.of(new QuoteLine(OIL, 1), new QuoteLine(LOAFERS, 1)), DeliveryMode.GROUND);
        assertThat(mixed.foodOnly()).isFalse();
    }

    @Test
    void airCostsMoreThanGround() {
        List<QuoteLine> lines = List.of(new QuoteLine(LOAFERS, 1));
        assertThat(pricing.quote(lines, DeliveryMode.AIR).deliveryRub())
                .isGreaterThan(pricing.quote(lines, DeliveryMode.GROUND).deliveryRub());
    }

    private static ProductSnapshot product(long id, Category c, long cents, int grams) {
        return new ProductSnapshot(id, "p" + id, "seller", c, "00000000", cents, grams, List.of());
    }
}
