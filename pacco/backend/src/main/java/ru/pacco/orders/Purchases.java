package ru.pacco.orders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.PurchaseOrder;

/** Покупки аккаунта для других модулей: «подтверждённая покупка» в отзывах и привязка чата к заказу. */
@Service
public class Purchases {

    private static final Set<OrderStatus> RECEIVED = Set.of(OrderStatus.RECEIVED, OrderStatus.COMPLETED);
    /** Строки, которые дошли до покупателя (в том числе возвращённые по спору). */
    private static final Set<LineState> DELIVERED = Set.of(LineState.ACCEPTED, LineState.SHIPPED, LineState.RETURNED);

    public record Purchase(String orderNumber, String size, Instant receivedAt) {
    }

    private final OrderRepository orders;

    public Purchases(OrderRepository orders) {
        this.orders = orders;
    }

    /** Получал ли покупатель этот товар — самый ранний такой заказ. */
    @Transactional(readOnly = true)
    public Optional<Purchase> received(String sub, long productId) {
        if (sub == null) return Optional.empty();
        List<PurchaseOrder> mine = orders.findByBuyerSubOrderByIdDesc(sub);
        for (int i = mine.size() - 1; i >= 0; i--) {
            PurchaseOrder o = mine.get(i);
            if (!RECEIVED.contains(o.getStatus())) continue;
            for (OrderLine l : o.getLines()) {
                if (l.getProductId() == productId && DELIVERED.contains(l.getState())) {
                    return Optional.of(new Purchase(o.getNumber(), l.getSize(), o.getReceivedAt()));
                }
            }
        }
        return Optional.empty();
    }

    /** Заказ принадлежит аккаунту и в нём есть этот товар (или товар не важен, если productId null). */
    @Transactional(readOnly = true)
    public boolean owns(String sub, String orderNumber, Long productId) {
        if (sub == null || orderNumber == null) return false;
        return orders.findByNumber(orderNumber)
                .filter(o -> sub.equals(o.getBuyerSub()))
                .filter(o -> productId == null || o.getLines().stream().anyMatch(l -> l.getProductId() == productId))
                .isPresent();
    }
}
