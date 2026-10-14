package ru.pacco.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.orders.internal.OrderLine;
import ru.pacco.orders.internal.OrderRepository;
import ru.pacco.orders.internal.Payout;
import ru.pacco.orders.internal.PayoutRepository;
import ru.pacco.orders.internal.PurchaseOrder;
import ru.pacco.sellers.SellerDirectory;
import ru.pacco.sellers.SellerView;
import ru.pacco.shared.BusinessException;

/**
 * Реестры выплат. Финансы формируют реестр из завершённых и ещё не выплаченных строк, переводят деньги
 * и отмечают выплату номером платёжного поручения. По каждой выплате есть отчёт агента и счёт на комиссию.
 */
@Service
public class Payouts {

    public record Summary(long id, String number, long sellerId, String sellerName, long grossEurCents,
                          long commissionEurCents, long netEurCents, int linesCount, String status, String bankRef,
                          Instant createdAt, Instant paidAt) {
    }

    public record Line(String orderNumber, int lineNo, String title, String size, int qty, String category,
                       long grossEurCents, BigDecimal rate, long commissionEurCents, Instant completedAt) {
    }

    /** Выплата с реквизитами сторон и строками — данные для документов. */
    public record Detail(Summary payout, SellerView seller, List<Line> lines) {
    }

    private final OrderRepository orders;
    private final PayoutRepository payouts;
    private final CommissionProperties commission;
    private final SellerDirectory sellers;

    public Payouts(OrderRepository orders, PayoutRepository payouts, CommissionProperties commission, SellerDirectory sellers) {
        this.orders = orders;
        this.payouts = payouts;
        this.commission = commission;
        this.sellers = sellers;
    }

    /** Сформировать реестр: по выплате на каждого продавца, у которого есть завершённые невыплаченные строки. */
    @Transactional
    public List<Summary> createBatch() {
        Instant now = Instant.now();
        Map<Long, Payout> bySeller = new LinkedHashMap<>();
        for (PurchaseOrder o : orders.findWithSellerLines()) {
            if (o.getStatus() != OrderStatus.COMPLETED) {
                continue;
            }
            for (OrderLine l : o.getLines()) {
                if (l.getSellerId() == null || !l.isActive() || l.getPayoutId() != null) {
                    continue;
                }
                Payout p = bySeller.computeIfAbsent(l.getSellerId(), s -> payouts.save(new Payout(
                        "ILP-%d-%05d".formatted(Year.now().getValue(), payouts.nextNumber()), s, now)));
                long gross = l.getPriceEurCents() * l.getQty();
                long fee = BigDecimal.valueOf(gross).multiply(commission.rate(l.getCategory()))
                        .setScale(0, RoundingMode.HALF_UP).longValue();
                p.add(gross, fee);
                l.includeIn(p.getId(), fee);
            }
        }
        if (bySeller.isEmpty()) {
            throw BusinessException.unprocessable("nothing_to_pay", "Нет завершённых заказов, которые ещё не вошли в реестр");
        }
        return bySeller.values().stream().map(this::summary).toList();
    }

    @Transactional
    public Summary markPaid(long id, String bankRef) {
        Payout p = payouts.findById(id).orElseThrow(() -> BusinessException.notFound("Выплата"));
        if (!"READY".equals(p.getStatus())) {
            throw BusinessException.unprocessable("already_paid", "Выплата уже отмечена");
        }
        if (bankRef == null || bankRef.isBlank()) {
            throw BusinessException.unprocessable("bank_ref_required", "Укажите номер платёжного поручения");
        }
        p.paid(bankRef.trim(), Instant.now());
        return summary(p);
    }

    @Transactional(readOnly = true)
    public List<Summary> all() {
        return payouts.findAllByOrderByIdDesc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<Summary> ofSeller(long sellerId) {
        return payouts.findBySellerIdOrderByIdDesc(sellerId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Detail detail(long id, Long requiredSeller) {
        Payout p = payouts.findById(id)
                .filter(x -> requiredSeller == null || x.getSellerId() == requiredSeller)
                .orElseThrow(() -> BusinessException.notFound("Выплата"));
        List<Line> lines = new ArrayList<>();
        for (PurchaseOrder o : orders.findByPayout(p.getId())) {
            Instant completed = o.getEvents().stream().filter(e -> e.getStatus() == OrderStatus.COMPLETED)
                    .map(e -> e.getAt()).reduce((a, b) -> b).orElse(o.getUpdatedAt());
            for (int i = 0; i < o.getLines().size(); i++) {
                OrderLine l = o.getLines().get(i);
                if (p.getId().equals(l.getPayoutId())) {
                    lines.add(new Line(o.getNumber(), i, l.getTitle(), l.getSize(), l.getQty(), l.getCategory().name(),
                            l.getPriceEurCents() * l.getQty(), commission.rate(l.getCategory()),
                            l.getCommissionEurCents() == null ? 0 : l.getCommissionEurCents(), completed));
                }
            }
        }
        return new Detail(summary(p), sellers.get(p.getSellerId()), lines);
    }

    private Summary summary(Payout p) {
        String name;
        try {
            name = sellers.get(p.getSellerId()).legalName();
        } catch (BusinessException e) {
            name = "—";
        }
        return new Summary(p.getId(), p.getNumber(), p.getSellerId(), name, p.getGrossEurCents(), p.getCommissionEurCents(),
                p.getNetEurCents(), p.getLinesCount(), p.getStatus(), p.getBankRef(), p.getCreatedAt(), p.getPaidAt());
    }
}
