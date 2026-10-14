package ru.pacco.orders.internal;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.orders.SellerOrders;
import ru.pacco.sellers.SellerDirectory;

@RestController
class SellerOrderController {

    private final SellerOrders orders;
    private final SellerDirectory sellers;

    SellerOrderController(SellerOrders orders, SellerDirectory sellers) {
        this.orders = orders;
        this.sellers = sellers;
    }

    @GetMapping("/api/seller/orders")
    List<SellerOrders.View> list(@AuthenticationPrincipal Jwt jwt) {
        return orders.list(sellers.requireActive(jwt.getSubject()).id());
    }

    @PostMapping("/api/seller/orders/{number}/confirm")
    SellerOrders.View confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable String number) {
        return orders.confirm(number, sellers.requireActive(jwt.getSubject()).id());
    }

    @GetMapping("/api/seller/payouts")
    SellerOrders.Payouts payouts(@AuthenticationPrincipal Jwt jwt) {
        return orders.payouts(sellers.requireActive(jwt.getSubject()).id());
    }
}
