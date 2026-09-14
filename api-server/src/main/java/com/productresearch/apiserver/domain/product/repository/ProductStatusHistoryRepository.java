package com.productresearch.apiserver.domain.product.repository;

import com.productresearch.apiserver.domain.product.entity.ProductStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStatusHistoryRepository extends JpaRepository<ProductStatusHistory, Long> {}
