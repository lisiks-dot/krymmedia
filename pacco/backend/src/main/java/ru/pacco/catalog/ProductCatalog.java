package ru.pacco.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.catalog.internal.Product;
import ru.pacco.catalog.internal.ProductRepository;
import ru.pacco.catalog.internal.ProductStatus;
import ru.pacco.compliance.ComplianceService;
import ru.pacco.compliance.Violation;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.Category;
import ru.pacco.shared.ProductSnapshot;

/** Публичный API каталога для других модулей. */
@Service
public class ProductCatalog {

    /** Причина снятия, когда поставщик распродал товар; при появлении товар возвращается в продажу. */
    public static final String OUT_OF_STOCK = "Нет в наличии у поставщика";

    private final ProductRepository products;
    private final ComplianceService compliance;

    public ProductCatalog(ProductRepository products, ComplianceService compliance) {
        this.products = products;
        this.compliance = compliance;
    }

    /** Опубликованный товар; снятые с продажи и несуществующие — пусто. */
    @Transactional(readOnly = true)
    public Optional<ProductSnapshot> findPublished(long id) {
        return products.findById(id)
                .filter(p -> p.getStatus() == ProductStatus.PUBLISHED)
                .map(p -> p.toSnapshot());
    }

    /** Товар площадки (без продавца-партнёра), например из импорта каталога поставщика. */
    public record NewOperatorProduct(String sellerName, String title, Category category, String hsCode,
                                     long priceEurCents, int weightGrams, List<String> sizes, String description) {
    }

    /** Публикует товар площадки после проверки комплаенса; нарушение правил — 422 compliance_failed. */
    @Transactional
    public long publishOperatorProduct(NewOperatorProduct n) {
        Product p = new Product(n.sellerName(), n.title(), null, n.category(), n.hsCode(), n.priceEurCents(),
                n.weightGrams(), n.sizes(), n.description(), false);
        List<Violation> violations = compliance.check(p.toItemSpec());
        if (!violations.isEmpty()) {
            throw BusinessException.unprocessable("compliance_failed", "Товар нельзя публиковать",
                    violations.stream().map(Violation::toString).toList());
        }
        return products.save(p).getId();
    }

    /**
     * Обновление предложения поставщика: цена и размеры. Нет в наличии — снимаем с продажи;
     * снова в наличии — возвращаем, если проходит правила (цена могла вырасти выше порога).
     */
    @Transactional
    public void updateOffer(long id, long priceEurCents, List<String> sizes, boolean available) {
        Product p = products.findById(id).orElseThrow(() -> BusinessException.notFound("Товар"));
        if (p.getStatus() == ProductStatus.REJECTED) {
            return;
        }
        p.updateOffer(priceEurCents, sizes);
        if (!available) {
            p.block(OUT_OF_STOCK);
            return;
        }
        List<Violation> violations = compliance.check(p.toItemSpec());
        if (!violations.isEmpty()) {
            p.block(violations.getFirst().toString());
        } else if (p.getStatus() == ProductStatus.BLOCKED) {
            p.publish();
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> statusOf(long id) {
        return products.findById(id).map(p -> p.getStatus().name());
    }
}
