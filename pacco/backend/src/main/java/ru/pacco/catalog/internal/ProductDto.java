package ru.pacco.catalog.internal;

import java.util.List;

import ru.pacco.shared.Category;

/** Товар для витрины и админки. Цена в рублях уже включает курс и валютный буфер. */
public record ProductDto(
        long id,
        String title,
        String sellerName,
        String region,
        Category category,
        String hsCode,
        long priceEurCents,
        long priceRub,
        int weightGrams,
        List<String> sizes,
        String description,
        ProductStatus status,
        String blockReason,
        Long sellerId) {

    static ProductDto of(Product p, long priceRub) {
        return new ProductDto(p.getId(), p.getTitle(), p.getSellerName(), p.getRegion(), p.getCategory(),
                p.getHsCode(), p.getPriceEurCents(), priceRub, p.getWeightGrams(), p.sizeList(),
                p.getDescription(), p.getStatus(), p.getBlockReason(), p.getSellerId());
    }
}
