package ru.pacco.orders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.orders.internal.OrderEventRepository;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.PurchaseOrder;

/**
 * Исходящая очередь уведомлений (transactional outbox): события заказа с флагом notify
 * записываются в той же транзакции, что и смена статуса, а рассылка забирает их отдельно.
 */
@Service
public class OrderNotices {

    public record Notice(long eventId, long orderId, String number, OrderStatus status, String title, String note,
                         String buyerName, String phone, String email, String buyerSub, UUID key,
                         String pickupPoint, String pickupCode, LocalDate storageUntil, long totalRub, long refundRub,
                         String carrierTracking) {
    }

    private final OrderEventRepository events;
    private final OrderRepository orders;

    public OrderNotices(OrderEventRepository events, OrderRepository orders) {
        this.events = events;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<Notice> pending(int limit) {
        return events.outbox(PageRequest.of(0, limit)).stream().map(e -> {
            PurchaseOrder o = orders.findById(e.getOrderId()).orElseThrow();
            return new Notice(e.getId(), o.getId(), o.getNumber(), e.getStatus(), e.getTitle(), e.getNote(),
                    o.getBuyerName(), o.getBuyerPhone(), o.getBuyerEmail(), o.getBuyerSub(), o.getAccessKey(),
                    o.getPickupPoint(), o.getPickupCode(), o.getStorageUntil(), o.getTotalRub(), o.getRefundRub(),
                    o.getCarrierTracking());
        }).toList();
    }

    @Transactional
    public void done(long eventId) {
        events.findById(eventId).ifPresent(e -> e.notified(Instant.now()));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Long> orderId(String number) {
        return orders.findByNumber(number).map(PurchaseOrder::getId);
    }

    /** Заказы аккаунта — для ленты уведомлений в кабинете. */
    @Transactional(readOnly = true)
    public List<Long> orderIdsOf(String sub) {
        return orders.findByBuyerSubOrderByIdDesc(sub).stream().map(PurchaseOrder::getId).toList();
    }
}
