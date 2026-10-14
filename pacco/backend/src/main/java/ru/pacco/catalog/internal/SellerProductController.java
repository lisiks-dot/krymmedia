package ru.pacco.catalog.internal;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.compliance.ComplianceService;
import ru.pacco.compliance.ItemSpec;
import ru.pacco.compliance.Violation;
import ru.pacco.pricing.PricingService;
import ru.pacco.sellers.SellerDirectory;
import ru.pacco.sellers.SellerView;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.Category;

/** Карточки продавца: комплаенс при сохранении, затем модерация. */
@RestController
class SellerProductController {

    private final ProductRepository products;
    private final ComplianceService compliance;
    private final PricingService pricing;
    private final SellerDirectory sellers;

    SellerProductController(ProductRepository products, ComplianceService compliance, PricingService pricing,
                            SellerDirectory sellers) {
        this.products = products;
        this.compliance = compliance;
        this.pricing = pricing;
        this.sellers = sellers;
    }

    @GetMapping("/api/seller/products")
    @Transactional(readOnly = true)
    List<ProductDto> mine(@AuthenticationPrincipal Jwt jwt) {
        SellerView me = sellers.requireActive(jwt.getSubject());
        return products.findBySellerIdOrderById(me.id()).stream()
                .map(p -> ProductDto.of(p, pricing.rub(p.getPriceEurCents())))
                .toList();
    }

    @PostMapping("/api/seller/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ProductDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewProduct req) {
        SellerView me = sellers.requireActive(jwt.getSubject());
        Product p = Product.bySeller(me.id(), me.legalName(), req.title().trim(), me.region(), req.category(),
                req.hsCode().replaceAll("\\s", ""), req.priceEurCents(), req.weightGrams(), req.sizes(),
                req.description(), Boolean.TRUE.equals(req.aerosol()));
        requireCompliant(p.toItemSpec());
        return ProductDto.of(products.save(p), pricing.rub(p.getPriceEurCents()));
    }

    @PutMapping("/api/seller/products/{id}/price")
    @Transactional
    ProductDto price(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody NewPrice req) {
        SellerView me = sellers.requireActive(jwt.getSubject());
        Product p = products.findById(id).filter(x -> Long.valueOf(me.id()).equals(x.getSellerId()))
                .orElseThrow(() -> BusinessException.notFound("Товар"));
        requireCompliant(new ItemSpec(p.getCategory(), p.getHsCode(), req.priceEurCents(), p.isAerosol()));
        p.changePrice(req.priceEurCents());
        return ProductDto.of(p, pricing.rub(p.getPriceEurCents()));
    }

    private void requireCompliant(ItemSpec spec) {
        List<Violation> violations = compliance.check(spec);
        if (!violations.isEmpty()) {
            throw BusinessException.unprocessable("compliance_failed", "Товар нельзя публиковать",
                    violations.stream().map(Violation::toString).toList());
        }
    }

    record NewProduct(
            @NotBlank String title,
            @NotNull Category category,
            @NotBlank String hsCode,
            @Positive long priceEurCents,
            @Positive int weightGrams,
            List<String> sizes,
            String description,
            Boolean aerosol) {
    }

    record NewPrice(@Positive long priceEurCents) {
    }
}
