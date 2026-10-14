package ru.pacco.notifications.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.orders.OrderNotices;

@RestController
class NotificationController {

    record View(long id, Long orderId, String channel, String recipient, String subject, String body, String status,
                String provider, String error, Instant createdAt) {
        static View of(Notification n) {
            return new View(n.getId(), n.getOrderId(), n.getChannel(), n.getRecipient(), n.getSubject(), n.getBody(),
                    n.getStatus(), n.getProvider(), n.getError(), n.getCreatedAt());
        }
    }

    private final NotificationRepository journal;
    private final OrderNotices orders;

    NotificationController(NotificationRepository journal, OrderNotices orders) {
        this.journal = journal;
        this.orders = orders;
    }

    @GetMapping("/api/admin/notifications")
    List<View> all(@RequestParam(required = false) String order, @RequestParam(defaultValue = "100") int limit) {
        if (order != null && !order.isBlank()) {
            return orders.orderId(order.trim()).map(id -> journal.findByOrderIdOrderByIdDesc(id).stream().map(View::of).toList())
                    .orElse(List.of());
        }
        return journal.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 500))).stream().map(View::of).toList();
    }

    /** Лента сообщений покупателя — по всем заказам аккаунта, включая привязанные позже. */
    @GetMapping("/api/me/notifications")
    List<View> mine(@AuthenticationPrincipal Jwt jwt) {
        List<Long> ids = orders.orderIdsOf(jwt.getSubject());
        if (ids.isEmpty()) {
            return List.of();
        }
        return journal.findByOrderIdInOrderByIdDesc(ids, PageRequest.of(0, 50)).stream().map(View::of).toList();
    }
}
