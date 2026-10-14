package ru.pacco.catalog.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findBySellerIdOrderById(Long sellerId);

    List<Product> findByStatusOrderById(ProductStatus status);
}
