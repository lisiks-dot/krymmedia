package ru.pacco.orders.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.orders.CheckoutRequest;
import ru.pacco.orders.DisputeRequest;
import ru.pacco.orders.OrderService;
import ru.pacco.orders.OrderView;
import ru.pacco.orders.PayRequest;

/**
 * Заказ покупателя. Действия требуют ключ заказа (заголовок X-Order-Key): его получает браузер при оформлении,
 * а в рабочей системе — ещё и ссылка из СМС. Без ключа номер заказа показывает только публичный трекинг.
 */
@RestController
class OrderController {

    static final String KEY = "X-Order-Key";

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderView checkout(@Valid @RequestBody CheckoutRequest req) {
        return orders.checkout(req);
    }

    @GetMapping("/api/orders/{number}")
    OrderView get(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key) {
        return orders.get(number, key);
    }

    /** Возврат с платёжной страницы. В MVP оплата подтверждается заглушкой. */
    @PostMapping("/api/orders/{number}/pay")
    OrderView pay(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key,
                  @RequestBody(required = false) PayRequest req) {
        return orders.pay(number, key, req == null ? null : req.method());
    }

    @PostMapping("/api/orders/{number}/cancel")
    OrderView cancel(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key) {
        return orders.cancel(number, key);
    }

    @PostMapping("/api/orders/{number}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    OrderView dispute(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key,
                      @Valid @RequestBody DisputeRequest req) {
        return orders.dispute(number, key, req);
    }

    @GetMapping("/api/admin/orders")
    List<OrderView> recent(@RequestParam(defaultValue = "50") int limit) {
        return orders.recent(Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/api/admin/orders/{number}")
    OrderView staffView(@PathVariable String number) {
        return orders.staffView(number);
    }

    @PostMapping("/api/admin/orders/{number}/advance")
    OrderView advance(@PathVariable String number) {
        return orders.advance(number);
    }

    record Reason(String reason) {
    }

    @PostMapping("/api/admin/orders/{number}/cancel")
    OrderView staffCancel(@PathVariable String number, @RequestBody(required = false) Reason body) {
        return orders.staffCancel(number, body == null ? null : body.reason());
    }
}
