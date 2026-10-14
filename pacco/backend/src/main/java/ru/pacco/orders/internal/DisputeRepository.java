package ru.pacco.orders.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.pacco.orders.DisputeStatus;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    List<Dispute> findByStatusOrderByIdDesc(DisputeStatus status);

    List<Dispute> findAllByOrderByIdDesc();

    Optional<Dispute> findFirstByOrderIdOrderByIdDesc(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, DisputeStatus status);
}
