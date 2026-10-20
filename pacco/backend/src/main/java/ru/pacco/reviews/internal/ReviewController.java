package ru.pacco.reviews.internal;

import java.util.List;

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

import ru.pacco.buyers.BuyerProfiles;
import ru.pacco.catalog.ProductCatalog;
import ru.pacco.reviews.ReviewService;
import ru.pacco.sellers.SellerDirectory;
import ru.pacco.sellers.SellerView;

@RestController
class ReviewController {

    private final ReviewService reviews;
    private final BuyerProfiles profiles;
    private final SellerDirectory sellers;
    private final ProductCatalog catalog;

    ReviewController(ReviewService reviews, BuyerProfiles profiles, SellerDirectory sellers, ProductCatalog catalog) {
        this.reviews = reviews;
        this.profiles = profiles;
        this.sellers = sellers;
        this.catalog = catalog;
    }

    @GetMapping("/api/products/{id}/reviews")
    ReviewService.ProductReviews list(@PathVariable long id) {
        return reviews.forProduct(id);
    }

    @GetMapping("/api/products/{id}/reviews/me")
    ReviewService.Eligibility me(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return reviews.eligibility(jwt.getSubject(), id);
    }

    record NewReview(int rating, String text) {
    }

    @PostMapping("/api/products/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    ReviewService.ReviewView create(@PathVariable long id, @AuthenticationPrincipal Jwt jwt, @RequestBody NewReview body) {
        return reviews.create(jwt.getSubject(), displayName(jwt), id, body.rating(), body.text());
    }

    record Reply(String text) {
    }

    @GetMapping("/api/seller/reviews")
    List<ReviewService.ReviewView> sellerList(@AuthenticationPrincipal Jwt jwt) {
        SellerView me = sellers.requireActive(jwt.getSubject());
        return reviews.ofSeller(catalog.idsOfSeller(me.id()));
    }

    @PostMapping("/api/seller/reviews/{id}/replies")
    ReviewService.ReviewView sellerReply(@PathVariable long id, @AuthenticationPrincipal Jwt jwt, @RequestBody Reply body) {
        SellerView me = sellers.requireActive(jwt.getSubject());
        return reviews.sellerReply(id, me.id(), me.legalName(), body.text());
    }

    @GetMapping("/api/admin/reviews")
    List<ReviewService.ReviewView> staffList(@RequestParam(required = false) Boolean hidden) {
        return reviews.staffList(hidden);
    }

    @PostMapping("/api/admin/reviews/{id}/replies")
    ReviewService.ReviewView staffReply(@PathVariable long id, @RequestBody Reply body) {
        return reviews.staffReply(id, body.text());
    }

    record Reason(String reason) {
    }

    @PostMapping("/api/admin/reviews/{id}/hide")
    ReviewService.ReviewView hide(@PathVariable long id, @RequestBody(required = false) Reason body) {
        return reviews.hide(id, body == null ? null : body.reason());
    }

    @PostMapping("/api/admin/reviews/{id}/show")
    ReviewService.ReviewView show(@PathVariable long id) {
        return reviews.show(id);
    }

    /** Имя в отзыве: «Анна С.» — из профиля или из аккаунта. Полную фамилию не показываем. */
    private String displayName(Jwt jwt) {
        String name = profiles.get(jwt.getSubject(), null).name();
        if (name == null || name.isBlank()) {
            String given = jwt.getClaimAsString("given_name");
            String family = jwt.getClaimAsString("family_name");
            name = given != null ? given + (family != null ? " " + family : "") : jwt.getClaimAsString("preferred_username");
        }
        return mask(name == null ? "Покупатель" : name);
    }

    static String mask(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder(parts[0]);
        if (parts.length > 1 && !parts[1].isEmpty()) out.append(' ').append(parts[1].charAt(0)).append('.');
        return out.length() > 100 ? out.substring(0, 100) : out.toString();
    }
}
