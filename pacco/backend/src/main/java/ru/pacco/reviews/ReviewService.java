package ru.pacco.reviews;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.catalog.ProductCatalog;
import ru.pacco.orders.Purchases;
import ru.pacco.reviews.internal.Review;
import ru.pacco.reviews.internal.ReviewRepository;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.ProductSnapshot;

/**
 * Отзывы. Написать может только покупатель с аккаунтом, получивший этот товар (подтверждённая покупка),
 * один отзыв на товар. Отвечают продавец товара и ilpacco; модератор может скрыть отзыв с причиной.
 */
@Service
public class ReviewService {

    public record ReplyView(long id, String role, String author, String text, Instant createdAt) {
    }

    public record ReviewView(long id, long productId, String productTitle, Long sellerId, String author, String size,
                             int rating, String text, boolean verified, boolean hidden, String hiddenReason,
                             String orderNumber, Instant createdAt, List<ReplyView> replies) {
    }

    public record Summary(int count, double average, int[] distribution) {
    }

    public record ProductReviews(Summary summary, List<ReviewView> reviews) {
    }

    public record Eligibility(boolean canReview, String reason, String orderNumber, ReviewView mine) {
    }

    private final ReviewRepository reviews;
    private final Purchases purchases;
    private final ProductCatalog catalog;

    public ReviewService(ReviewRepository reviews, Purchases purchases, ProductCatalog catalog) {
        this.reviews = reviews;
        this.purchases = purchases;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public ProductReviews forProduct(long productId) {
        List<Review> list = reviews.findByProductIdAndHiddenFalseOrderByCreatedAtDesc(productId);
        int[] dist = new int[5];
        double sum = 0;
        for (Review r : list) {
            dist[r.getRating() - 1]++;
            sum += r.getRating();
        }
        double avg = list.isEmpty() ? 0 : Math.round(sum / list.size() * 10) / 10.0;
        return new ProductReviews(new Summary(list.size(), avg, dist), list.stream().map(r -> view(r, null, false)).toList());
    }

    @Transactional(readOnly = true)
    public Eligibility eligibility(String sub, long productId) {
        Optional<Review> existing = reviews.findByProductIdAndBuyerSub(productId, sub);
        if (existing.isPresent()) {
            return new Eligibility(false, "Вы уже оставили отзыв на этот товар", existing.get().getOrderNumber(), view(existing.get(), null, true));
        }
        return purchases.received(sub, productId)
                .map(p -> new Eligibility(true, null, p.orderNumber(), null))
                .orElse(new Eligibility(false, "Отзыв можно оставить после получения заказа с этим товаром", null, null));
    }

    @Transactional
    public ReviewView create(String sub, String authorName, long productId, int rating, String text) {
        if (rating < 1 || rating > 5) throw BusinessException.unprocessable("bad_rating", "Оценка — от 1 до 5");
        String body = text == null ? "" : text.trim();
        if (body.length() < 10) throw BusinessException.unprocessable("text_short", "Напишите хотя бы пару предложений — от 10 символов");
        if (body.length() > 2000) throw BusinessException.unprocessable("text_long", "Отзыв — до 2000 символов");
        if (reviews.findByProductIdAndBuyerSub(productId, sub).isPresent()) {
            throw BusinessException.unprocessable("already_reviewed", "Вы уже оставили отзыв на этот товар");
        }
        Purchases.Purchase p = purchases.received(sub, productId)
                .orElseThrow(() -> new BusinessException(403, "not_purchased", "Отзыв можно оставить после получения заказа с этим товаром", List.of()));
        Review r = reviews.save(new Review(productId, sub, authorName, p.orderNumber(), p.size(), rating, body));
        return view(r, null, true);
    }

    /** Ответ продавца: только на отзывы о своих товарах. */
    @Transactional
    public ReviewView sellerReply(long reviewId, long sellerId, String sellerName, String text) {
        Review r = require(reviewId);
        Long owner = catalog.find(r.getProductId()).map(ProductSnapshot::sellerId).orElse(null);
        if (owner == null || owner != sellerId) throw BusinessException.notFound("Отзыв");
        r.reply("SELLER", sellerName, clean(text));
        return view(r, null, false);
    }

    @Transactional
    public ReviewView staffReply(long reviewId, String text) {
        Review r = require(reviewId);
        r.reply("ADMIN", "ilpacco", clean(text));
        return view(r, null, true);
    }

    @Transactional
    public ReviewView hide(long reviewId, String reason) {
        Review r = require(reviewId);
        r.hide(reason == null || reason.isBlank() ? "Нарушает правила площадки" : reason.trim());
        return view(r, null, true);
    }

    @Transactional
    public ReviewView show(long reviewId) {
        Review r = require(reviewId);
        r.show();
        return view(r, null, true);
    }

    /** Отзывы о товарах продавца, новые сверху. */
    @Transactional(readOnly = true)
    public List<ReviewView> ofSeller(List<Long> productIds) {
        if (productIds.isEmpty()) return List.of();
        return reviews.findByProductIdInOrderByCreatedAtDesc(productIds).stream().map(r -> view(r, null, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewView> staffList(Boolean hidden) {
        List<Review> list = hidden == null ? reviews.findAllByOrderByIdDesc(PageRequest.of(0, 200))
                : reviews.findByHiddenOrderByIdDesc(hidden, PageRequest.of(0, 200));
        return list.stream().map(r -> view(r, null, true)).toList();
    }

    private Review require(long id) {
        return reviews.findById(id).orElseThrow(() -> BusinessException.notFound("Отзыв"));
    }

    private static String clean(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw BusinessException.unprocessable("text_required", "Напишите ответ");
        if (t.length() > 2000) throw BusinessException.unprocessable("text_long", "Ответ — до 2000 символов");
        return t;
    }

    private ReviewView view(Review r, String ignored, boolean staff) {
        Optional<ProductSnapshot> p = catalog.find(r.getProductId());
        return new ReviewView(r.getId(), r.getProductId(), p.map(ProductSnapshot::title).orElse(null), p.map(ProductSnapshot::sellerId).orElse(null),
                r.getAuthorName(), r.getSize(), r.getRating(), r.getText(), true, r.isHidden(), r.getHiddenReason(),
                staff ? r.getOrderNumber() : null, r.getCreatedAt(),
                r.getReplies().stream().map(x -> new ReplyView(x.getId(), x.getAuthorRole(), x.getAuthorName(), x.getText(), x.getCreatedAt())).toList());
    }
}
