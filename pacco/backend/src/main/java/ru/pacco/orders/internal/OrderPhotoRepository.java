package ru.pacco.orders.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPhotoRepository extends JpaRepository<OrderPhoto, Long> {

    /** Только метаданные, без байтов (закрытая проекция). */
    List<Meta> findByOrderIdOrderById(Long orderId);

    long countByOrderIdAndKind(Long orderId, String kind);

    Optional<OrderPhoto> findByIdAndOrderId(Long id, Long orderId);

    interface Meta {
        Long getId();
        Integer getLineNo();
        String getKind();
        Instant getCreatedAt();
    }
}
