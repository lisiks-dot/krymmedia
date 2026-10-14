package ru.pacco.orders;

import java.time.Instant;
import java.util.List;

import ru.pacco.pricing.DeliveryMode;
import ru.pacco.shared.Category;

public record OrderView(
        String number,
        OrderStatus status,
        String statusLabel,
        int statusStep,
        DeliveryMode mode,
        String buyerName,
        String pickupPoint,
        List<Line> items,
        long goodsRub,
        long deliveryRub,
        long dutyRub,
        long vatRub,
        long totalRub,
        Instant createdAt,
        Instant updatedAt) {

    public record Line(long productId, String title, String sellerName, Category category, String size, int qty,
                       long priceEurCents) {
    }
}
