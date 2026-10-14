package ru.pacco.orders.internal;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.orders.CheckoutRequest;
import ru.pacco.orders.OrderService;
import ru.pacco.orders.OrderView;

@RestController
class OrderController {

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
    OrderView get(@PathVariable String number) {
        return orders.get(number);
    }

    /** Возврат с платёжной страницы. В MVP оплата подтверждается заглушкой. */
    @PostMapping("/api/orders/{number}/pay")
    OrderView pay(@PathVariable String number) {
        return orders.pay(number);
    }

    @GetMapping("/api/admin/orders")
    List<OrderView> recent(@RequestParam(defaultValue = "50") int limit) {
        return orders.recent(Math.min(Math.max(limit, 1), 200));
    }

    @PostMapping("/api/admin/orders/{number}/advance")
    OrderView advance(@PathVariable String number) {
        return orders.advance(number);
    }
}
