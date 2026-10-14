package ru.pacco.orders.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.orders.Payouts;
import ru.pacco.sellers.SellerDirectory;

/** Реестры выплат: финансы (/api/admin/payouts/batches) и продавец (/api/seller/payouts/history). */
@RestController
class PayoutController {

    private final Payouts payouts;
    private final SellerDirectory sellers;

    PayoutController(Payouts payouts, SellerDirectory sellers) {
        this.payouts = payouts;
        this.sellers = sellers;
    }

    @GetMapping("/api/admin/payouts/batches")
    List<Payouts.Summary> all() {
        return payouts.all();
    }

    @PostMapping("/api/admin/payouts/batches")
    @ResponseStatus(HttpStatus.CREATED)
    List<Payouts.Summary> create() {
        return payouts.createBatch();
    }

    @GetMapping("/api/admin/payouts/batches/{id}")
    Payouts.Detail detail(@PathVariable long id) {
        return payouts.detail(id, null);
    }

    record Paid(String bankRef) {
    }

    @PostMapping("/api/admin/payouts/batches/{id}/paid")
    Payouts.Summary paid(@PathVariable long id, @RequestBody Paid body) {
        return payouts.markPaid(id, body.bankRef());
    }

    @GetMapping("/api/seller/payouts/history")
    List<Payouts.Summary> history(@AuthenticationPrincipal Jwt jwt) {
        return payouts.ofSeller(sellers.requireActive(jwt.getSubject()).id());
    }

    @GetMapping("/api/seller/payouts/history/{id}")
    Payouts.Detail sellerDetail(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return payouts.detail(id, sellers.requireActive(jwt.getSubject()).id());
    }
}
