package ru.pacco.sourcing.internal;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    List<ImportJob> findAllByOrderByIdDesc(Pageable page);
}
