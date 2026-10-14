package ru.pacco.orders.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ru.pacco.orders.CheckoutRequest;
import ru.pacco.orders.DisputeRequest;
import ru.pacco.orders.OrderPhotos;
import ru.pacco.orders.OrderService;
import ru.pacco.orders.OrderView;
import ru.pacco.orders.PayRequest;

/**
 * Заказ покупателя. Владельца узнаём по ключу заказа (заголовок X-Order-Key: браузер, где оформлен заказ,
 * или ссылка из СМС) либо по аккаунту (Bearer-токен Keycloak). Без них номер показывает только публичный трекинг.
 */
@RestController
class OrderController {

    static final String KEY = "X-Order-Key";

    private final OrderService orders;
    private final OrderPhotos photos;

    OrderController(OrderService orders, OrderPhotos photos) {
        this.orders = orders;
        this.photos = photos;
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderView checkout(@Valid @RequestBody CheckoutRequest req, @AuthenticationPrincipal Jwt jwt) {
        return orders.checkout(req, sub(jwt));
    }

    @GetMapping("/api/orders/{number}")
    OrderView get(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key,
                  @AuthenticationPrincipal Jwt jwt) {
        return orders.get(number, key, sub(jwt));
    }

    /** Возврат с платёжной страницы. В MVP оплата подтверждается заглушкой. */
    @PostMapping("/api/orders/{number}/pay")
    OrderView pay(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key,
                  @AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) PayRequest req) {
        return orders.pay(number, key, sub(jwt), req == null ? null : req.method());
    }

    @PostMapping("/api/orders/{number}/cancel")
    OrderView cancel(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key,
                     @AuthenticationPrincipal Jwt jwt) {
        return orders.cancel(number, key, sub(jwt));
    }

    @PostMapping("/api/orders/{number}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    OrderView dispute(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key,
                      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DisputeRequest req) {
        return orders.dispute(number, key, sub(jwt), req);
    }

    @PostMapping(path = "/api/orders/{number}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    OrderView.PhotoView disputePhoto(@PathVariable String number, @RequestHeader(name = KEY, required = false) UUID key,
                                     @AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file,
                                     @RequestParam(required = false) Integer lineNo) {
        return photos.disputeUpload(number, key, sub(jwt), lineNo, file);
    }

    @GetMapping("/api/orders/{number}/photos/{id}")
    ResponseEntity<byte[]> buyerPhoto(@PathVariable String number, @PathVariable long id,
                                      @RequestHeader(name = KEY, required = false) UUID key, @AuthenticationPrincipal Jwt jwt) {
        return image(photos.forBuyer(number, key, sub(jwt), id));
    }

    @GetMapping("/api/me/orders")
    List<OrderView> mine(@AuthenticationPrincipal Jwt jwt) {
        return orders.mine(jwt.getSubject());
    }

    record ClaimBody(List<OrderService.Claim> orders) {
    }

    @PostMapping("/api/me/orders/claim")
    List<OrderView> claim(@AuthenticationPrincipal Jwt jwt, @RequestBody ClaimBody body) {
        return orders.claim(jwt.getSubject(), body.orders());
    }

    @GetMapping("/api/admin/orders")
    List<OrderView> recent(@RequestParam(defaultValue = "50") int limit) {
        return orders.recent(Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/api/admin/orders/{number}")
    OrderView staffView(@PathVariable String number) {
        return orders.staffView(number);
    }

    @GetMapping("/api/admin/orders/{number}/photos/{id}")
    ResponseEntity<byte[]> staffPhoto(@PathVariable String number, @PathVariable long id) {
        return image(photos.forStaff(number, id));
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

    static ResponseEntity<byte[]> image(OrderPhotos.Photo p) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.contentType()))
                .cacheControl(CacheControl.noStore()).body(p.data());
    }

    private static String sub(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
