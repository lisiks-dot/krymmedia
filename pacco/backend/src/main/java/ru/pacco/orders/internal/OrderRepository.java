package ru.pacco.orders.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByNumber(String number);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextNumber();

    @Query("select distinct o from PurchaseOrder o join o.lines l where l.sellerId = :sellerId order by o.id desc")
    List<PurchaseOrder> findBySeller(long sellerId);
}
