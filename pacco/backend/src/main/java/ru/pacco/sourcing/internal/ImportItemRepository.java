package ru.pacco.sourcing.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportItemRepository extends JpaRepository<ImportItem, Long> {

    Optional<ImportItem> findBySourceUrl(String sourceUrl);

    List<ImportItem> findByStatusOrderByIdDesc(String status, Pageable page);

    List<ImportItem> findByStatusOrderByIdAsc(String status);

    long countByStatus(String status);
}
