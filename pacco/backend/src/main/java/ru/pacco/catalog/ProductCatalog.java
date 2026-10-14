package ru.pacco.catalog;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.catalog.internal.ProductRepository;
import ru.pacco.catalog.internal.ProductStatus;
import ru.pacco.shared.ProductSnapshot;

/** Публичный API каталога для других модулей. */
@Service
public class ProductCatalog {

    private final ProductRepository products;

    public ProductCatalog(ProductRepository products) {
        this.products = products;
    }

    /** Опубликованный товар; снятые с продажи и несуществующие — пусто. */
    @Transactional(readOnly = true)
    public Optional<ProductSnapshot> findPublished(long id) {
        return products.findById(id)
                .filter(p -> p.getStatus() == ProductStatus.PUBLISHED)
                .map(p -> p.toSnapshot());
    }
}
