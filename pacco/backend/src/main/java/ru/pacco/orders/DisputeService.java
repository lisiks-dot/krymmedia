package ru.pacco.orders;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.orders.OrderViews.Audience;
import ru.pacco.orders.internal.Dispute;
import ru.pacco.orders.internal.DisputeRepository;
import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.PurchaseOrder;
import ru.pacco.shared.BusinessException;

/** Споры покупателей: очередь поддержки и решение — вернуть деньги за выбранные товары или отклонить. */
@Service
public class DisputeService {

    public record Case(OrderView.DisputeView dispute, OrderView order) {
    }

    private final DisputeRepository disputes;
    private final OrderViews views;

    public DisputeService(DisputeRepository disputes, OrderViews views) {
        this.disputes = disputes;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public List<Case> list(DisputeStatus status) {
        List<Dispute> all = status == null ? disputes.findAllByOrderByIdDesc() : disputes.findByStatusOrderByIdDesc(status);
        return all.stream().map(d -> new Case(OrderViews.dispute(d), views.view(d.getOrder(), Audience.STAFF))).toList();
    }

    @Transactional
    public Case resolve(long id, boolean refund, String resolution) {
        Dispute d = disputes.findById(id).orElseThrow(() -> BusinessException.notFound("Спор"));
        if (d.getStatus() != DisputeStatus.OPEN) {
            throw BusinessException.unprocessable("dispute_closed", "Спор уже решён");
        }
        String text = resolution == null || resolution.isBlank() ? null : resolution.trim();
        PurchaseOrder o = d.getOrder();
        Instant now = Instant.now();
        if (refund) {
            long rub = 0;
            for (int n : d.lines()) {
                OrderLine l = o.line(n);
                if (l.isActive()) {
                    rub += o.lineRub(l);
                    l.close(LineState.RETURNED, d.getReason().label());
                }
            }
            o.refund(rub);
            d.resolve(DisputeStatus.REFUNDED, rub, text == null ? "Вернули деньги за товар" : text, now);
            o.logNotify("Спор решён: вернём " + rub + " ₽", "Деньги придут на карту за 1–3 рабочих дня. Товар отправлять обратно не нужно", "support", now);
        } else {
            if (text == null) {
                throw BusinessException.unprocessable("resolution_required", "Объясните покупателю, почему спор отклонён");
            }
            d.resolve(DisputeStatus.REJECTED, 0, text, now);
            o.logNotify("Спор отклонён", text, "support", now);
        }
        return new Case(OrderViews.dispute(d), views.view(o, Audience.STAFF));
    }
}
