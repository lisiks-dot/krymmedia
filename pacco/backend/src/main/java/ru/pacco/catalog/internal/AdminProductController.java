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

    AdminProductController(ProductRepository products, ComplianceService compliance, PricingService pricing) {
        this.products = products;
        this.compliance = compliance;
        this.pricing = pricing;
    }

    @GetMapping("/api/admin/products")
    @Transactional(readOnly = true)
    List<ProductDto> all() {
        return products.findAll(Sort.by("id")).stream()
                .map(p -> ProductDto.of(p, pricing.rub(p.getPriceEurCents())))
                .toList();
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
