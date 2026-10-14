package ru.pacco.orders.internal;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    @Query("select e from OrderEvent e where e.notify = true and e.notifiedAt is null order by e.id")
    List<OrderEvent> outbox(Pageable page);
}
