package ru.pacco.catalog.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.pricing.PricingService;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.Category;

/** Публичный каталог: только опубликованные товары. */
@RestController
class ProductController {

    private final ProductRepository products;
    private final PricingService pricing;

    ProductController(ProductRepository products, PricingService pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    @GetMapping("/api/products")
    @Transactional(readOnly = true)
    List<ProductDto> list(@RequestParam(required = false) Category category,
                          @RequestParam(required = false) String q) {
        String needle = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), ProductStatus.PUBLISHED));
            if (category != null) {
                ps.add(cb.equal(root.get("category"), category));
            }
            if (needle != null) {
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("title")), needle),
                        cb.like(cb.lower(root.get("sellerName")), needle),
                        cb.like(cb.lower(root.get("region")), needle)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return products.findAll(spec, Sort.by("id")).stream()
                .map(p -> ProductDto.of(p, pricing.rub(p.getPriceEurCents())))
                .toList();
    }

    @GetMapping("/api/products/{id}")
    @Transactional(readOnly = true)
    ProductDto get(@PathVariable long id) {
        return products.findById(id)
                .filter(p -> p.getStatus() == ProductStatus.PUBLISHED)
                .map(p -> ProductDto.of(p, pricing.rub(p.getPriceEurCents())))
                .orElseThrow(() -> BusinessException.notFound("Товар"));
    }
}
