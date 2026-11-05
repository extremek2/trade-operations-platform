package com.tradeoperationsplatform.apiserver.domain.product.repository;

import com.tradeoperationsplatform.apiserver.domain.product.entity.ProductStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStatusHistoryRepository extends JpaRepository<ProductStatusHistory, Long> {}
