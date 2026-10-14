package ru.pacco.orders.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    List<Payout> findAllByOrderByIdDesc();

    List<Payout> findBySellerIdOrderByIdDesc(long sellerId);

    @Query(value = "select nextval('payout_number_seq')", nativeQuery = true)
    long nextNumber();
}
