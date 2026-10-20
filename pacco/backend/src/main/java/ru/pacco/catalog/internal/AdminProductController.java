package ru.pacco.catalog.internal;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.compliance.ComplianceService;
import ru.pacco.compliance.Violation;
import ru.pacco.pricing.PricingService;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.Category;

/** Модерация каталога: все товары, включая снятые, и публикация новых с проверкой комплаенса. */
@RestController
class AdminProductController {

    private final ProductRepository products;
    private final ComplianceService compliance;
    private final PricingService pricing;
    private final MediaService media;

    AdminProductController(ProductRepository products, ComplianceService compliance, PricingService pricing, MediaService media) {
        this.products = products;
        this.compliance = compliance;
        this.pricing = pricing;
        this.media = media;
    }

    /** Модератор убирает неподходящее фото или видео. */
    @org.springframework.web.bind.annotation.DeleteMapping("/api/admin/products/{id}/media/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMedia(@PathVariable long id, @PathVariable long mediaId) {
        media.delete(id, mediaId);
    }

    @GetMapping("/api/admin/products")
    @Transactional(readOnly = true)
    List<ProductDto> all() {
        List<Product> all = products.findAll(Sort.by("id"));
        var byProduct = media.of(all.stream().map(Product::getId).toList());
        return all.stream()
                .map(p -> ProductDto.of(p, pricing.rub(p.getPriceEurCents()), byProduct.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @GetMapping("/api/admin/products/pending")
    @Transactional(readOnly = true)
    List<ProductDto> pending() {
        List<Product> pending = products.findByStatusOrderById(ProductStatus.PENDING);
        var byProduct = media.of(pending.stream().map(Product::getId).toList());
        return pending.stream()
                .map(p -> ProductDto.of(p, pricing.rub(p.getPriceEurCents()), byProduct.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    /** Модератор публикует карточку продавца; комплаенс проверяется ещё раз по действующим правилам. */
    @PostMapping("/api/admin/products/{id}/approve")
    @Transactional
    ProductDto approve(@PathVariable long id) {
        Product p = products.findById(id).orElseThrow(() -> BusinessException.notFound("Товар"));
        if (p.getStatus() != ProductStatus.PENDING) {
            throw BusinessException.unprocessable("not_pending", "Карточка не ждёт модерации");
        }
        List<Violation> violations = compliance.check(p.toItemSpec());
        if (!violations.isEmpty()) {
            p.reject(violations.getFirst().toString());
            throw BusinessException.unprocessable("compliance_failed", "Товар нельзя публиковать",
                    violations.stream().map(Violation::toString).toList());
        }
        p.publish();
        return ProductDto.of(p, pricing.rub(p.getPriceEurCents()));
    }

    @PostMapping("/api/admin/products/{id}/reject")
    @Transactional
    ProductDto reject(@PathVariable long id, @RequestBody(required = false) Reason reason) {
        Product p = products.findById(id).orElseThrow(() -> BusinessException.notFound("Товар"));
        p.reject(reason == null || reason.reason() == null || reason.reason().isBlank()
                ? "Отклонено модератором" : reason.reason().trim());
        return ProductDto.of(p, pricing.rub(p.getPriceEurCents()));
    }

    record Reason(String reason) {
    }

    @PostMapping("/api/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ProductDto create(@Valid @RequestBody CreateProduct req) {
        Product p = new Product(req.sellerName(), req.title(), req.region(), req.category(), req.hsCode(),
                req.priceEurCents(), req.weightGrams(), req.sizes(), req.description(), req.aerosol());
        List<Violation> violations = compliance.check(p.toItemSpec());
        if (!violations.isEmpty()) {
            throw BusinessException.unprocessable("compliance_failed", "Товар нельзя публиковать",
                    violations.stream().map(Violation::toString).toList());
        }
        return ProductDto.of(products.save(p), pricing.rub(p.getPriceEurCents()));
    }

    record CreateProduct(
            @NotBlank String sellerName,
            @NotBlank String title,
            String region,
            @NotNull Category category,
            @NotBlank String hsCode,
            @Positive long priceEurCents,
            @Positive int weightGrams,
            List<String> sizes,
            String description,
            boolean aerosol) {
    }
}
