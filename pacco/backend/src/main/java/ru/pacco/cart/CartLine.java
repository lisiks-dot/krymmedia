package ru.pacco.cart;

import ru.pacco.shared.ProductSnapshot;

/** Строка корзины: опубликованный товар, размер (если есть) и количество. */
public record CartLine(ProductSnapshot product, String size, int qty) {
}
