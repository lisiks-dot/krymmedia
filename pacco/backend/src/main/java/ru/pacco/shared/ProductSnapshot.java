package ru.pacco.shared;

import java.util.List;

/** Неизменяемый срез опубликованного товара, которым модули обмениваются между собой. */
public record ProductSnapshot(
        long id,
        String title,
        String sellerName,
        Category category,
        String hsCode,
        long priceEurCents,
        int weightGrams,
        List<String> sizes) {

    public ProductSnapshot {
        sizes = sizes == null ? List.of() : List.copyOf(sizes);
    }
}
