package ru.pacco.orders.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByNumber(String number);

    List<PurchaseOrder> findByBuyerSubOrderByIdDesc(String buyerSub);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextNumber();

    @Query("select distinct o from PurchaseOrder o join o.lines l where l.sellerId = :sellerId order by o.id desc")
    List<PurchaseOrder> findBySeller(long sellerId);

    List<PurchaseOrder> findByStatusInOrderByIdAsc(java.util.Collection<ru.pacco.orders.OrderStatus> statuses);

    List<PurchaseOrder> findByStatusAndReceivedAtBefore(ru.pacco.orders.OrderStatus status, java.time.Instant before);

    @Query("select distinct o from PurchaseOrder o join o.lines l where l.sellerId is not null and o.status <> ru.pacco.orders.OrderStatus.CREATED")
    List<PurchaseOrder> findWithSellerLines();

    @Query("select distinct o from PurchaseOrder o join o.lines l where l.payoutId = :payoutId order by o.id")
    List<PurchaseOrder> findByPayout(Long payoutId);
}
