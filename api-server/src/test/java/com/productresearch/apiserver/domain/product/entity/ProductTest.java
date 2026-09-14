package com.productresearch.apiserver.domain.product.entity;

import com.productresearch.apiserver.domain.identity.entity.AppUser;
import com.productresearch.apiserver.domain.identity.entity.Organization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

    private final Organization organization = new Organization(
            "테스트 조직", Organization.Type.SHIPPER, null, null, null);
    private final AppUser creator = AppUser.registered(
            "owner@example.test", "encoded", "담당자", null);

    @Test
    void candidateAndSellableProductShareOneLifecycle() {
        Product product = new Product(organization, creator, "휴대용 선풍기", "여름 수요", null, null, "생활");

        assertThat(product.getStatus()).isEqualTo(Product.Status.DISCOVERED);
        assertThat(product.changeStatus(Product.Status.SOURCING)).isEqualTo(Product.Status.DISCOVERED);
        assertThat(product.changeStatus(Product.Status.REVIEWING)).isEqualTo(Product.Status.SOURCING);
        assertThat(product.changeStatus(Product.Status.APPROVED)).isEqualTo(Product.Status.REVIEWING);
        assertThat(product.changeStatus(Product.Status.ACTIVE)).isEqualTo(Product.Status.APPROVED);
    }

    @Test
    void lifecycleCannotSkipRequiredReview() {
        Product product = new Product(organization, creator, "상품", null, null, null, null);

        assertThatThrownBy(() -> product.changeStatus(Product.Status.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISCOVERED -> ACTIVE");
    }
}
