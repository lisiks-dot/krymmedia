package ru.pacco.cart.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.cart.CartLine;
import ru.pacco.cart.CartService;
import ru.pacco.pricing.DeliveryMode;
import ru.pacco.pricing.PricingService;
import ru.pacco.pricing.Quote;
import ru.pacco.pricing.QuoteLine;
import ru.pacco.shared.Category;

@RestController
class CartController {

    private final CartService carts;
    private final PricingService pricing;

    CartController(CartService carts, PricingService pricing) {
        this.carts = carts;
        this.pricing = pricing;
    }

    @PostMapping("/api/carts")
    @ResponseStatus(HttpStatus.CREATED)
    CartId create() {
        return new CartId(carts.create());
    }

    @GetMapping("/api/carts/{id}")
    CartView view(@PathVariable UUID id, @RequestParam(defaultValue = "GROUND") DeliveryMode mode) {
        return render(id, mode);
    }

    @PostMapping("/api/carts/{id}/items")
    CartView add(@PathVariable UUID id, @Valid @RequestBody AddItem req,
                 @RequestParam(defaultValue = "GROUND") DeliveryMode mode) {
        carts.add(id, req.productId(), req.size(), req.qty() == null ? 1 : req.qty());
        return render(id, mode);
    }

    @PutMapping("/api/carts/{id}/items")
    CartView set(@PathVariable UUID id, @Valid @RequestBody SetItem req,
                 @RequestParam(defaultValue = "GROUND") DeliveryMode mode) {
        carts.setQty(id, req.productId(), req.size(), req.qty());
        return render(id, mode);
    }

    private CartView render(UUID id, DeliveryMode mode) {
        List<CartLine> lines = carts.lines(id);
        List<Item> items = lines.stream().map(l -> new Item(l.product().id(), l.product().title(),
                l.product().sellerName(), l.product().category(), l.size(), l.qty(),
                l.product().priceEurCents(), pricing.rub(l.product().priceEurCents()))).toList();
        Quote quote = lines.isEmpty() ? null
                : pricing.quote(lines.stream().map(l -> new QuoteLine(l.product(), l.qty())).toList(), mode);
        return new CartView(id, items, quote);
    }

    record CartId(UUID id) {
    }

    record AddItem(@Positive long productId, String size, @Min(1) @Max(20) Integer qty) {
    }

    record SetItem(@Positive long productId, String size, @Min(0) @Max(20) int qty) {
    }

    record Item(long productId, String title, String sellerName, Category category, String size, int qty,
                long unitPriceEurCents, long unitPriceRub) {
    }

    record CartView(UUID id, List<Item> items, Quote quote) {
    }
}
