package ru.pacco.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ru.pacco.shared.Category;

/** Расчёт цены «под ключ»: товары, доставка, пошлина ЕАЭС и сценарий НДС. */
@Service
public class PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN = BigDecimal.TEN;

    private final PricingProperties props;

    public PricingService(PricingProperties props) {
        this.props = props;
    }

    /** Цена в рублях: курс × (1 + буфер), округление вверх до 10 ₽. */
    public long rub(long eurCents) {
        if (eurCents <= 0) {
            return 0;
        }
        BigDecimal rub = BigDecimal.valueOf(eurCents).divide(HUNDRED)
                .multiply(props.rubPerEur())
                .multiply(BigDecimal.ONE.add(props.fxBuffer()));
        return rub.divide(TEN, 0, RoundingMode.CEILING).multiply(TEN).longValueExact();
    }

    public Quote quote(List<QuoteLine> lines, DeliveryMode mode) {
        long goods = lines.stream().mapToLong(l -> l.product().priceEurCents() * l.qty()).sum();
        int rawGrams = lines.stream().mapToInt(l -> l.product().weightGrams() * l.qty()).sum() + props.packagingGrams();
        int grams = Math.max(100, (int) Math.ceil(rawGrams / 100.0) * 100);
        BigDecimal kg = BigDecimal.valueOf(grams).divide(BigDecimal.valueOf(1000));

        Map<DeliveryMode, Long> byMode = new EnumMap<>(DeliveryMode.class);
        for (DeliveryMode m : DeliveryMode.values()) {
            byMode.put(m, rub(deliveryCents(m, kg)));
        }
        long delivery = deliveryCents(mode, kg);

        long duty = 0;
        if (goods > props.dutyFreeEurCents()) {
            long byValue = BigDecimal.valueOf(goods).multiply(props.dutyRate()).setScale(0, RoundingMode.HALF_UP).longValue();
            long byWeight = kg.multiply(props.dutyMinEurPerKg()).multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValue();
            duty = Math.max(byValue, byWeight);
        }
        long vat = props.vatEnabled()
                ? BigDecimal.valueOf(goods + duty).multiply(props.vatRate()).setScale(0, RoundingMode.HALF_UP).longValue()
                : 0;

        long goodsRub = lines.stream().mapToLong(l -> rub(l.product().priceEurCents()) * l.qty()).sum();
        long deliveryRub = rub(delivery);
        long dutyRub = rub(duty);
        long vatRub = vat > 0 ? rub(vat) + props.parcelFeeRub() : 0;

        boolean foodOnly = !lines.isEmpty() && lines.stream().allMatch(l -> l.product().category() == Category.FOOD);

        return new Quote(mode, goods, grams, delivery, duty, vat,
                goodsRub, deliveryRub, dutyRub, vatRub, goodsRub + deliveryRub + dutyRub + vatRub,
                duty == 0, foodOnly, removalHint(lines, goods), byMode, props.rubPerEur());
    }

    private long deliveryCents(DeliveryMode mode, BigDecimal kg) {
        PricingProperties.Tariff t = props.tariff(mode);
        return t.perParcelEur().add(t.perKgEur().multiply(kg)).multiply(HUNDRED)
                .setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** Самый дешёвый товар, без которого посылка уложится в беспошлинный лимит. */
    private Long removalHint(List<QuoteLine> lines, long goods) {
        if (goods <= props.dutyFreeEurCents()) {
            return null;
        }
        long need = goods - props.dutyFreeEurCents();
        return lines.stream()
                .filter(l -> l.product().priceEurCents() >= need)
                .min(Comparator.comparingLong(l -> l.product().priceEurCents()))
                .map(l -> l.product().id())
                .orElse(null);
    }
}
