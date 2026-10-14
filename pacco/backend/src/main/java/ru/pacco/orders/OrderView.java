package ru.pacco.orders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ru.pacco.pricing.DeliveryMode;
import ru.pacco.shared.Category;

/**
 * Заказ для покупателя, поддержки и операций. Без ключа покупателя часть полей скрыта:
 * по одному номеру заказа нельзя узнать имя полностью и код получения.
 */
public record OrderView(
        String number,
        OrderStatus status,
        String statusLabel,
        int statusStep,
        DeliveryMode mode,
        String buyerName,
        String buyerPhone,
        String pickupPoint,
        List<Line> items,
        long goodsRub,
        long deliveryRub,
        long dutyRub,
        long vatRub,
        long totalRub,
        long refundRub,
        PaymentMethod paymentMethod,
        String cancelReason,
        Integer parcelWeightG,
        String carrierTracking,
        String pickupCode,
        LocalDate storageUntil,
        Instant receivedAt,
        Instant disputeUntil,
        boolean canPay,
        boolean canCancel,
        boolean canDispute,
        UUID accessKey,
        List<Event> events,
        DisputeView dispute,
        List<PhotoView> photos,
        String buyerEmail,
        Long pickupPointId,
        boolean linkedToAccount,
        Instant createdAt,
        Instant updatedAt) {

    public record Line(int lineNo, long productId, String title, String sellerName, Long sellerId, Category category,
                       String size, int qty, long priceEurCents, long lineRub, LineState state, String stateLabel,
                       String sellerTracking, String note) {
    }

    public record Event(OrderStatus status, String title, String note, String actor, Instant at) {
    }

    public record PhotoView(long id, Integer lineNo, String kind, Instant createdAt) {
    }

    public record DisputeView(long id, List<Integer> lines, DisputeReason reason, String reasonLabel, String comment,
                              DisputeStatus status, String statusLabel, long refundRub, String resolution,
                              Instant createdAt, Instant resolvedAt) {
    }
}
