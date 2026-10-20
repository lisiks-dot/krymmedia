package ru.pacco.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, Long> {

    List<ProductMedia> findByProductIdOrderByPositionAscIdAsc(long productId);

    List<ProductMedia> findByProductIdInOrderByProductIdAscPositionAscIdAsc(Collection<Long> productIds);

    Optional<ProductMedia> findByIdAndProductId(long id, long productId);

    long countByProductIdAndKind(long productId, String kind);
}
