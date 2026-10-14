package ru.pacco.catalog.internal;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.compliance.ComplianceRulesChanged;
import ru.pacco.compliance.ComplianceService;
import ru.pacco.compliance.Violation;

/**
 * Перепроверяет весь каталог при изменении правил: нарушающие товары снимаются с продажи,
 * снятые ранее возвращаются, если новые правила это позволяют. Работает в той же транзакции,
 * что и изменение правил, — администратор сразу видит результат.
 */
@Component
class CatalogComplianceListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogComplianceListener.class);

    private final ProductRepository products;
    private final ComplianceService compliance;

    CatalogComplianceListener(ProductRepository products, ComplianceService compliance) {
        this.products = products;
        this.compliance = compliance;
    }

    @EventListener
    @Transactional
    void on(ComplianceRulesChanged event) {
        int blocked = 0;
        int restored = 0;
        for (Product p : products.findAll()) {
            if (p.getStatus() == ProductStatus.REJECTED) {
                continue;
            }
            List<Violation> v = compliance.check(p.toItemSpec());
            if (p.getStatus() == ProductStatus.PENDING) {
                if (!v.isEmpty()) {
                    p.reject(v.getFirst().toString());
                }
                continue;
            }
            if (!v.isEmpty() && p.getStatus() == ProductStatus.PUBLISHED) {
                p.block(v.getFirst().toString());
                blocked++;
            } else if (v.isEmpty() && p.getStatus() == ProductStatus.BLOCKED) {
                p.publish();
                restored++;
            } else if (!v.isEmpty()) {
                p.block(v.getFirst().toString());
            }
        }
        log.info("Каталог перепроверен по новым правилам: снято {}, возвращено {}", blocked, restored);
    }
}
