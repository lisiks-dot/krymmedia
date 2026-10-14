package ru.pacco.cart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import ru.pacco.catalog.ProductCatalog;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.ProductSnapshot;

/**
 * Корзины гостей и покупателей в Redis: хеш {@code pacco:cart:<id>}, поле {@code <productId>|<size>}, значение — количество.
 * Живут 30 дней с последнего изменения.
 */
@Service
public class CartService {

    private static final Duration TTL = Duration.ofDays(30);
    private static final int MAX_QTY = 20;

    private final StringRedisTemplate redis;
    private final ProductCatalog catalog;

    public CartService(StringRedisTemplate redis, ProductCatalog catalog) {
        this.redis = redis;
        this.catalog = catalog;
    }

    public UUID create() {
        return UUID.randomUUID();
    }

    public void add(UUID cartId, long productId, String size, int qty) {
        ProductSnapshot p = requireProduct(productId);
        String normalized = normalizeSize(p, size);
        String key = key(cartId);
        Long now = redis.opsForHash().increment(key, field(productId, normalized), qty);
        if (now != null && now > MAX_QTY) {
            redis.opsForHash().put(key, field(productId, normalized), String.valueOf(MAX_QTY));
        }
        redis.expire(key, TTL);
    }

    public void setQty(UUID cartId, long productId, String size, int qty) {
        String key = key(cartId);
        String f = field(productId, size == null || size.isBlank() ? null : size.trim());
        if (qty <= 0) {
            redis.opsForHash().delete(key, f);
        } else {
            redis.opsForHash().put(key, f, String.valueOf(Math.min(qty, MAX_QTY)));
            redis.expire(key, TTL);
        }
    }

    /** Строки корзины; товары, снятые с продажи после добавления, отбрасываются. */
    public List<CartLine> lines(UUID cartId) {
        Map<Object, Object> raw = redis.opsForHash().entries(key(cartId));
        List<CartLine> out = new ArrayList<>();
        raw.forEach((k, v) -> {
            String[] parts = k.toString().split("\\|", -1);
            long productId = Long.parseLong(parts[0]);
            String size = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
            int qty = Integer.parseInt(v.toString());
            catalog.findPublished(productId).ifPresent(p -> out.add(new CartLine(p, size, qty)));
        });
        out.sort(Comparator.comparingLong((CartLine l) -> l.product().id()).thenComparing(l -> l.size() == null ? "" : l.size()));
        return out;
    }

    public void clear(UUID cartId) {
        redis.delete(key(cartId));
    }

    private ProductSnapshot requireProduct(long productId) {
        return catalog.findPublished(productId).orElseThrow(() -> BusinessException.notFound("Товар"));
    }

    private static String normalizeSize(ProductSnapshot p, String size) {
        String s = size == null || size.isBlank() ? null : size.trim();
        if (p.sizes().isEmpty()) {
            return null;
        }
        if (s == null) {
            throw BusinessException.unprocessable("size_required", "Выберите размер");
        }
        if (!p.sizes().contains(s)) {
            throw BusinessException.unprocessable("size_unknown", "Такого размера нет: " + s);
        }
        return s;
    }

    private static String key(UUID cartId) {
        return "pacco:cart:" + cartId;
    }

    private static String field(long productId, String size) {
        return productId + "|" + (size == null ? "" : size);
    }
}
