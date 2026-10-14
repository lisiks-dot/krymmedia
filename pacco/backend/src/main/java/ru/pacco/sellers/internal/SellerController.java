package ru.pacco.sellers.internal;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.sellers.SellerApplication;
import ru.pacco.sellers.SellerDirectory;
import ru.pacco.sellers.SellerStatus;
import ru.pacco.sellers.SellerView;
import ru.pacco.shared.BusinessException;

@RestController
class SellerController {

    private final SellerDirectory sellers;

    SellerController(SellerDirectory sellers) {
        this.sellers = sellers;
    }

    /** Профиль продавца текущего пользователя; 404 — заявки ещё нет. */
    @GetMapping("/api/seller/me")
    SellerView me(@AuthenticationPrincipal Jwt jwt) {
        return sellers.bySubject(jwt.getSubject()).orElseThrow(() -> BusinessException.notFound("Профиль продавца"));
    }

    @PostMapping("/api/seller/me")
    @ResponseStatus(HttpStatus.CREATED)
    SellerView apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SellerApplication application) {
        return sellers.apply(jwt.getSubject(), application);
    }

    @GetMapping("/api/admin/sellers")
    List<SellerView> list(@RequestParam(required = false) SellerStatus status) {
        return sellers.list(status);
    }

    @PostMapping("/api/admin/sellers/{id}/approve")
    SellerView approve(@PathVariable long id) {
        return sellers.approve(id);
    }

    @PostMapping("/api/admin/sellers/{id}/reject")
    SellerView reject(@PathVariable long id, @RequestBody(required = false) Reason reason) {
        return sellers.reject(id, reason == null ? null : reason.reason());
    }

    record Reason(String reason) {
    }
}
