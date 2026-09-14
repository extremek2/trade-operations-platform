package com.productresearch.apiserver.domain.product.repository;

import com.productresearch.apiserver.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrganizationIdOrderByUpdatedAtDesc(Long organizationId);
    Optional<Product> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
    boolean existsByOrganizationIdAndInternalSku(Long organizationId, String internalSku);
}
