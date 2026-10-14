package ru.pacco.pricing;

import ru.pacco.shared.ProductSnapshot;

public record QuoteLine(ProductSnapshot product, int qty) {
}
