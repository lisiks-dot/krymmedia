package ru.pacco.orders.internal;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.orders.SellerOrders;
import ru.pacco.sellers.SellerDirectory;
import ru.pacco.sellers.SellerStatus;
import ru.pacco.sellers.SellerView;

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
        return orders.list(id(jwt));
    }

    @GetMapping("/api/seller/orders/{number}")
    SellerOrders.View get(@AuthenticationPrincipal Jwt jwt, @PathVariable String number) {
        return orders.get(number, id(jwt));
    }

    @PostMapping("/api/seller/orders/{number}/confirm")
    SellerOrders.View confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable String number) {
        return orders.confirm(number, id(jwt));
    }

    record Decline(List<Integer> lines, String reason) {
    }

    @PostMapping("/api/seller/orders/{number}/decline")
    SellerOrders.View decline(@AuthenticationPrincipal Jwt jwt, @PathVariable String number, @RequestBody Decline body) {
        return orders.decline(number, id(jwt), body.lines(), body.reason());
    }

    record Ship(String tracking) {
    }

    @PostMapping("/api/seller/orders/{number}/ship")
    SellerOrders.View ship(@AuthenticationPrincipal Jwt jwt, @PathVariable String number, @RequestBody Ship body) {
        return orders.ship(number, id(jwt), body.tracking());
    }

    @GetMapping("/api/seller/payouts")
    SellerOrders.Payouts payouts(@AuthenticationPrincipal Jwt jwt) {
        return orders.payouts(id(jwt));
    }

    record RegistryRow(long sellerId, String legalName, String iban, SellerOrders.Payouts payouts) {
    }

    /** Реестр выплат для финансов: все активные продавцы, даже без заказов. */
    @GetMapping("/api/admin/payouts")
    List<RegistryRow> registry() {
        Map<Long, SellerOrders.Payouts> sums = orders.registry();
        SellerOrders.Payouts zero = new SellerOrders.Payouts(0, 0, 0, 0, 0, 0, 0);
        return sellers.list(SellerStatus.ACTIVE).stream()
                .map(s -> new RegistryRow(s.id(), s.legalName(), s.iban(), sums.getOrDefault(s.id(), zero)))
                .toList();
    }

    private long id(Jwt jwt) {
        SellerView s = sellers.requireActive(jwt.getSubject());
        return s.id();
    }
}
