package ru.pacco.sellers.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.pacco.sellers.SellerStatus;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    Optional<Seller> findByKeycloakSub(String keycloakSub);

    Optional<Seller> findByPartitaIva(String partitaIva);

    List<Seller> findByStatusOrderById(SellerStatus status);
}
