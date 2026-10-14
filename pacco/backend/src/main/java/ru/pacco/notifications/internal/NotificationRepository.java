package ru.pacco.notifications.internal;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByIdDesc(Pageable page);

    List<Notification> findByBuyerSubOrderByIdDesc(String buyerSub, Pageable page);

    List<Notification> findByOrderIdOrderByIdDesc(Long orderId);

    List<Notification> findByOrderIdInOrderByIdDesc(List<Long> orderIds, Pageable page);
}
