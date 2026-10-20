package ru.pacco.reviews.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndHiddenFalseOrderByCreatedAtDesc(long productId);

    Optional<Review> findByProductIdAndBuyerSub(long productId, String buyerSub);

    List<Review> findByProductIdInOrderByCreatedAtDesc(Collection<Long> productIds);

    List<Review> findAllByOrderByIdDesc(Pageable page);

    List<Review> findByHiddenOrderByIdDesc(boolean hidden, Pageable page);
}
