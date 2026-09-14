package com.tradeoperationsplatform.apiserver.domain.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeoperationsplatform.apiserver.PostgresTestSupport;
import com.tradeoperationsplatform.apiserver.domain.auth.dto.LoginRequest;
import com.tradeoperationsplatform.apiserver.domain.auth.service.AuthService;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.OrganizationMember;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.AppUserRepository;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.OrganizationMemberRepository;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductSourcingIntegrationTest extends PostgresTestSupport {
    private static final String PASSWORD = "test-password-1234";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired OrganizationRepository organizations;
    @Autowired OrganizationMemberRepository members;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;

    String ownerToken;
    String otherToken;

    @BeforeEach
    void setup() {
        ownerToken = createOwner("소싱 조직");
        otherToken = createOwner("다른 조직");
    }

    @Test
    void productCandidateFlowsIntoSupplierQuoteWithoutCreatingAnotherProduct() throws Exception {
        JsonNode product = createProduct(ownerToken, "휴대용 선풍기");
        JsonNode supplier = body(call(post("/api/v1/organizations/current/partners"), ownerToken,
                Map.of("name", "선전 공급사 " + UUID.randomUUID(), "roles", List.of("SUPPLIER")))
                .andExpect(status().isCreated()));

        JsonNode sourcingProduct = body(call(post("/api/v1/products/" + product.get("productId").asText() + "/status"),
                ownerToken, Map.of("status", "SOURCING", "reason", "공급가 확인", "version", 0))
                .andExpect(status().isOk()));
        assertThat(sourcingProduct.get("status").asText()).isEqualTo("SOURCING");
        assertThat(sourcingProduct.get("version").asLong()).isEqualTo(1);

        Map<String, Object> quoteRequest = Map.of(
                "supplierId", supplier.get("businessPartnerId").asText(),
                "quoteNumber", "Q-" + UUID.randomUUID(),
                "currency", "usd",
                "lines", new Object[]{Map.of(
                        "productId", product.get("productId").asText(),
                        "description", "가격 확인 중",
                        "minimumQuantity", 100,
                        "quantityUnit", "EA",
                        "countryOfOrigin", "cn")});
        JsonNode quote = body(call(post("/api/v1/quotes"), ownerToken, quoteRequest)
                .andExpect(status().isCreated()));

        assertThat(quote.get("status").asText()).isEqualTo("DRAFT");
        assertThat(quote.get("revisionNumber").asInt()).isEqualTo(1);
        assertThat(quote.at("/lines/0/productId").asText()).isEqualTo(product.get("productId").asText());
        assertThat(quote.at("/lines/0/unitPrice").isNull()).isTrue();

        JsonNode received = body(call(post("/api/v1/quotes/" + quote.get("quoteId").asText() + "/status"),
                ownerToken, Map.of("status", "RECEIVED", "version", 0))
                .andExpect(status().isOk()));
        assertThat(received.get("version").asLong()).isEqualTo(1);

        Map<String, Object> revisionRequest = Map.of(
                "currency", "USD",
                "version", 1,
                "lines", new Object[]{Map.of(
                        "productId", product.get("productId").asText(),
                        "description", "수정 견적",
                        "minimumQuantity", 80,
                        "quantityUnit", "EA",
                        "unitPrice", 4.25)});
        JsonNode revision = body(call(post("/api/v1/quotes/" + quote.get("quoteId").asText() + "/revisions"),
                ownerToken, revisionRequest).andExpect(status().isCreated()));
        assertThat(revision.get("revisionNumber").asInt()).isEqualTo(2);
        assertThat(revision.get("previousQuoteId").asText()).isEqualTo(quote.get("quoteId").asText());
        assertThat(revision.get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void draftCanBeEditedAndQuoteNumberIsScopedToSupplier() throws Exception {
        JsonNode product = createProduct(ownerToken, "수정 대상 상품");
        body(call(post("/api/v1/products/" + product.get("productId").asText() + "/status"), ownerToken,
                Map.of("status", "SOURCING", "reason", "견적 요청", "version", 0)).andExpect(status().isOk()));
        JsonNode firstSupplier = body(call(post("/api/v1/organizations/current/partners"), ownerToken,
                Map.of("name", "첫 공급사 " + UUID.randomUUID(), "roles", List.of("SUPPLIER"))).andExpect(status().isCreated()));
        JsonNode secondSupplier = body(call(post("/api/v1/organizations/current/partners"), ownerToken,
                Map.of("name", "둘째 공급사 " + UUID.randomUUID(), "roles", List.of("SUPPLIER"))).andExpect(status().isCreated()));
        String quoteNumber = "SHARED-001-" + UUID.randomUUID();
        Map<String, Object> line = Map.of("productId", product.get("productId").asText(), "description", "첫 조건");
        JsonNode first = body(call(post("/api/v1/quotes"), ownerToken, Map.of(
                "supplierId", firstSupplier.get("businessPartnerId").asText(), "quoteNumber", quoteNumber,
                "currency", "KRW", "lines", new Object[]{line})).andExpect(status().isCreated()));

        Map<String, Object> revisedLine = Map.of("productId", product.get("productId").asText(),
                "description", "수정 조건", "unitPrice", 1200);
        JsonNode revised = body(call(put("/api/v1/quotes/" + first.get("quoteId").asText()), ownerToken,
                Map.of("quoteNumber", quoteNumber, "currency", "KRW", "version", 0,
                        "lines", new Object[]{revisedLine})).andExpect(status().isOk()));
        assertThat(revised.get("version").asLong()).isEqualTo(1);
        assertThat(revised.at("/lines/0/unitPrice").decimalValue()).isEqualByComparingTo("1200");

        call(post("/api/v1/quotes"), ownerToken, Map.of(
                "supplierId", secondSupplier.get("businessPartnerId").asText(), "quoteNumber", quoteNumber,
                "currency", "KRW", "lines", new Object[]{line})).andExpect(status().isCreated());
    }

    @Test
    void organizationCannotReadOrReuseAnotherOrganizationsCatalog() throws Exception {
        JsonNode product = createProduct(ownerToken, "조직 전용 상품");
        JsonNode supplier = body(call(post("/api/v1/organizations/current/partners"), ownerToken,
                Map.of("name", "조직 전용 공급사 " + UUID.randomUUID(), "roles", List.of("SUPPLIER")))
                .andExpect(status().isCreated()));

        call(get("/api/v1/products/" + product.get("productId").asText()), otherToken, null)
                .andExpect(status().isNotFound());

        Map<String, Object> quoteRequest = Map.of(
                "supplierId", supplier.get("businessPartnerId").asText(),
                "currency", "KRW",
                "lines", new Object[]{Map.of(
                        "productId", product.get("productId").asText(),
                        "description", "다른 조직에서 재사용 시도")});
        call(post("/api/v1/quotes"), otherToken, quoteRequest)
                .andExpect(status().isNotFound());
    }

    @Test
    void quoteRejectsProductBeforeSourcingStarts() throws Exception {
        JsonNode product = createProduct(ownerToken, "아직 발굴 중인 상품");
        JsonNode supplier = body(call(post("/api/v1/organizations/current/partners"), ownerToken,
                Map.of("name", "검증 공급사 " + UUID.randomUUID(), "roles", List.of("SUPPLIER")))
                .andExpect(status().isCreated()));

        Map<String, Object> quoteRequest = Map.of(
                "supplierId", supplier.get("businessPartnerId").asText(),
                "currency", "KRW",
                "lines", new Object[]{Map.of(
                        "productId", product.get("productId").asText(),
                        "description", "상태 규칙 위반")});

        call(post("/api/v1/quotes"), ownerToken, quoteRequest)
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewerCanReadButCannotMutateCatalog() throws Exception {
        JsonNode product = createProduct(ownerToken, "읽기 전용 상품");
        String viewerToken = createMember("조회 조직", OrganizationMember.Role.VIEWER);

        call(get("/api/v1/products"), viewerToken, null).andExpect(status().isOk());
        call(post("/api/v1/products"), viewerToken,
                Map.of("name", "등록 불가 상품"))
                .andExpect(status().isForbidden());

        call(get("/api/v1/products/" + product.get("productId").asText()), viewerToken, null)
                .andExpect(status().isNotFound());
    }

    private JsonNode createProduct(String token, String name) throws Exception {
        return body(call(post("/api/v1/products"), token,
                Map.of("name", name + " " + UUID.randomUUID(), "hypothesis", "시장성 검증"))
                .andExpect(status().isCreated()));
    }

    private String createOwner(String organizationName) {
        return createMember(organizationName, OrganizationMember.Role.OWNER);
    }

    private String createMember(String organizationName, OrganizationMember.Role role) {
        Organization organization = organizations.save(new Organization(
                organizationName + " " + UUID.randomUUID(), Organization.Type.SHIPPER, null, null, null));
        AppUser user = users.save(AppUser.registered(
                UUID.randomUUID() + "@example.test", passwords.encode(PASSWORD), role.name(), null));
        members.save(new OrganizationMember(organization, user, role));
        return auth.login(new LoginRequest(user.getEmail(), PASSWORD, null)).response().accessToken();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String bearer, Object data) throws Exception {
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (data != null) request.contentType("application/json").content(json.writeValueAsString(data));
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}
