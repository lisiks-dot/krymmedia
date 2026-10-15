package ru.pacco.sourcing;

import java.util.List;

/**
 * Факты о товаре поставщика. Фото и описания не храним — только ссылку на первое фото для модератора.
 *
 * @param priceEurCents цена в евроцентах (минимальная по размерам), null — цену не нашли
 */
public record Scraped(String url, String sku, String brand, String title, String color, String categoryHint,
                      Long priceEurCents, String currency, List<String> sizes, boolean available, String imageUrl) {

    public Scraped {
        sizes = sizes == null ? List.of() : List.copyOf(sizes);
    }
}
