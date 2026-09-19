package com.tradeoperationsplatform.apiserver.domain.offer;

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
import com.tradeoperationsplatform.apiserver.domain.product.repository.ProductRepository;
import com.tradeoperationsplatform.apiserver.domain.sourcing.repository.SupplierQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupplierOfferIntegrationTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired OrganizationRepository organizations;
    @Autowired OrganizationMemberRepository members;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ProductRepository products;
    @Autowired SupplierQuoteRepository quotes;
    @Autowired SourceArtifactRepository artifacts;
    @Autowired SupplierOfferDraftRepository drafts;

    String owner;
    String other;
    String viewer;

    @BeforeEach void setup() {
        owner = member(OrganizationMember.Role.OWNER);
        other = member(OrganizationMember.Role.OWNER);
        viewer = member(OrganizationMember.Role.VIEWER);
    }

    @Test
    void importsXlsxPreservesItsIdentityAndReturnsSameDraftOnRetry() throws Exception {
        JsonNode supplier = body(call(post("/api/v1/organizations/current/partners"), owner,
                Map.of("name", "파일 제안 공급처 " + UUID.randomUUID(), "roles", List.of("SUPPLIER")))
                .andExpect(status().isCreated()));
        byte[] content = Objects.requireNonNull(getClass().getResourceAsStream(
                "/fixtures/supplier-offer-synthetic.xlsx")).readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "supplier-offer.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        long artifactCount = artifacts.count();
        long draftCount = drafts.count();

        call(multipart("/api/v1/supplier-offer-drafts/import")
                .param("supplierId", supplier.get("businessPartnerId").asText()).param("currency", "JPY"), owner, null)
                .andExpect(status().isBadRequest());
        call(multipart("/api/v1/supplier-offer-drafts/import")
                        .file(new MockMultipartFile("file", "offer.txt", "text/plain",
                                "상품명".getBytes(StandardCharsets.UTF_8)))
                        .param("supplierId", supplier.get("businessPartnerId").asText()).param("currency", "JPY"), owner, null)
                .andExpect(status().isBadRequest());

        JsonNode imported = body(call(multipart("/api/v1/supplier-offer-drafts/import").file(file)
                        .param("supplierId", supplier.get("businessPartnerId").asText())
                        .param("currency", "jpy").param("sourceReference", "synthetic fixture"), owner, null)
                .andExpect(status().isCreated()));

        assertThat(imported.get("sourceType").asText()).isEqualTo("XLSX");
        assertThat(imported.get("originalFileName").asText()).isEqualTo("supplier-offer.xlsx");
        assertThat(imported.get("fileSize").asLong()).isEqualTo(content.length);
        assertThat(imported.get("originalText").isNull()).isTrue();
        assertThat(imported.get("contentHash").asText()).hasSize(64);
        assertThat(imported.get("extractionMethod").asText()).isEqualTo("XLSX");
        assertThat(imported.get("extractorVersion").asText()).isEqualTo("tabular-v1");
        assertThat(imported.get("currency").asText()).isEqualTo("JPY");
        assertThat(imported.get("lines")).hasSize(4);
        assertThat(imported.at("/lines/0/reviewedName").asText()).isEqualTo("스테인리스 텀블러");
        assertThat(imported.at("/lines/0/status").asText()).isEqualTo("PENDING");
        assertThat(imported.at("/lines/2/unitPrice").isNull()).isTrue();
        assertThat(imported.at("/lines/2/extractionErrors").asText()).contains("단가가 숫자가 아닙니다");
        assertThat(imported.at("/lines/3/extractionErrors").asText()).contains("수량 단위가 없습니다");
        assertThat(artifacts.count()).isEqualTo(artifactCount + 1);
        assertThat(drafts.count()).isEqualTo(draftCount + 1);

        JsonNode retried = body(call(multipart("/api/v1/supplier-offer-drafts/import").file(file)
                        .param("supplierId", supplier.get("businessPartnerId").asText()).param("currency", "JPY"), owner, null)
                .andExpect(status().isCreated()));
        assertThat(retried.get("draftId").asText()).isEqualTo(imported.get("draftId").asText());
        assertThat(artifacts.count()).isEqualTo(artifactCount + 1);
        assertThat(drafts.count()).isEqualTo(draftCount + 1);

        call(get("/api/v1/supplier-offer-drafts/" + imported.get("draftId").asText()), other, null)
                .andExpect(status().isNotFound());
        call(multipart("/api/v1/supplier-offer-drafts/import").file(file)
                .param("supplierId", supplier.get("businessPartnerId").asText()).param("currency", "JPY"), viewer, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void manualOfferKeepsOriginalAndNeedsEveryLineReviewedWithoutPublishingCatalog() throws Exception {
        JsonNode supplier = body(call(post("/api/v1/organizations/current/partners"), owner,
                Map.of("name", "제안 공급처 " + UUID.randomUUID(), "roles", List.of("SUPPLIER")))
                .andExpect(status().isCreated()));
        long productCount = products.count();
        long quoteCount = quotes.count();
        String original = "원본 제안 " + UUID.randomUUID() + "\nA 280엔\nB 가격 미정";
        Map<String, Object> request = Map.of("supplierId", supplier.get("businessPartnerId").asText(),
                "originalText", original, "currency", "jpy",
                "lines", List.of(Map.of("originalName", "원본 A", "sourceLocation", "line 2"),
                        Map.of("originalName", "원본 B", "sourceLocation", "line 3")));
        JsonNode draft = body(call(post("/api/v1/supplier-offer-drafts"), owner, request)
                .andExpect(status().isCreated()));
        String id = draft.get("draftId").asText();
        assertThat(draft.get("originalText").asText()).isEqualTo(original);
        assertThat(draft.get("currency").asText()).isEqualTo("JPY");
        assertThat(draft.at("/lines/0/unitPrice").isNull()).isTrue();

        call(post("/api/v1/supplier-offer-drafts"), owner, request).andExpect(status().isConflict());
        call(get("/api/v1/supplier-offer-drafts/" + id), other, null).andExpect(status().isNotFound());
        call(put("/api/v1/supplier-offer-drafts/" + id + "/lines/1"), other,
                Map.of("version", 0, "reviewedName", "침범")).andExpect(status().isNotFound());
        call(post("/api/v1/supplier-offer-drafts/" + id + "/confirm"), owner,
                Map.of("version", 0)).andExpect(status().isBadRequest());

        draft = body(call(put("/api/v1/supplier-offer-drafts/" + id + "/lines/1"), owner,
                Map.of("version", 0, "reviewedName", "확인 A", "supplierSku", "A-1",
                        "quantityUnit", "EA", "minimumQuantity", 12, "unitPrice", 280))
                .andExpect(status().isOk()));
        assertThat(draft.get("version").asLong()).isEqualTo(1);
        assertThat(draft.at("/lines/0/originalName").asText()).isEqualTo("원본 A");
        assertThat(draft.at("/lines/0/reviewedName").asText()).isEqualTo("확인 A");
        call(post("/api/v1/supplier-offer-drafts/" + id + "/lines/2/exclude"), owner,
                Map.of("version", 0)).andExpect(status().isConflict());
        draft = body(call(post("/api/v1/supplier-offer-drafts/" + id + "/lines/2/exclude"), owner,
                Map.of("version", 1)).andExpect(status().isOk()));
        assertThat(draft.at("/lines/1/status").asText()).isEqualTo("EXCLUDED");
        draft = body(call(post("/api/v1/supplier-offer-drafts/" + id + "/confirm"), owner,
                Map.of("version", 2)).andExpect(status().isOk()));
        assertThat(draft.get("status").asText()).isEqualTo("CONFIRMED");
        call(put("/api/v1/supplier-offer-drafts/" + id + "/lines/1"), owner,
                Map.of("version", 3, "reviewedName", "늦은 수정")).andExpect(status().isBadRequest());
        assertThat(products.count()).isEqualTo(productCount);
        assertThat(quotes.count()).isEqualTo(quoteCount);
    }

    @Test
    void viewerCanReadButCannotChangeDraft() throws Exception {
        call(get("/api/v1/supplier-offer-drafts"), viewer, null).andExpect(status().isOk());
        call(post("/api/v1/supplier-offer-drafts"), viewer,
                Map.of("supplierId", UUID.randomUUID().toString(), "originalText", "원본",
                        "currency", "JPY", "lines", List.of(Map.of("originalName", "상품"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmedRowsCanBeSelectedInPartsAndOnlySelectedRowsFlowIntoCosting() throws Exception {
        JsonNode supplier = body(call(post("/api/v1/organizations/current/partners"), owner,
                Map.of("name", "부분 제안 공급처 " + UUID.randomUUID(), "roles", List.of("SUPPLIER")))
                .andExpect(status().isCreated()));
        JsonNode foreignProduct = body(call(post("/api/v1/products"), other,
                Map.of("name", "다른 조직 상품 " + UUID.randomUUID()))
                .andExpect(status().isCreated()));
        long productCount = products.count();
        long quoteCount = quotes.count();
        JsonNode draft = body(call(post("/api/v1/supplier-offer-drafts"), owner,
                Map.of("supplierId", supplier.get("businessPartnerId").asText(),
                        "originalText", "부분 제안 " + UUID.randomUUID(), "currency", "JPY",
                        "lines", List.of(Map.of("originalName", "원본 A"), Map.of("originalName", "원본 B"),
                                Map.of("originalName", "제외 C"))))
                .andExpect(status().isCreated()));
        String draftId = draft.get("draftId").asText();
        call(post("/api/v1/supplier-offer-drafts/" + draftId + "/selections"), owner,
                Map.of("version", 0, "lines", List.of(Map.of("lineNumber", 1, "desiredQuantity", 12))))
                .andExpect(status().isBadRequest());

        draft = body(call(put("/api/v1/supplier-offer-drafts/" + draftId + "/lines/1"), owner,
                Map.of("version", 0, "reviewedName", "확인 A", "quantityUnit", "EA",
                        "minimumQuantity", 10, "unitPrice", 280))
                .andExpect(status().isOk()));
        draft = body(call(put("/api/v1/supplier-offer-drafts/" + draftId + "/lines/2"), owner,
                Map.of("version", 1, "reviewedName", "확인 B", "quantityUnit", "EA",
                        "minimumQuantity", 5, "unitPrice", 190))
                .andExpect(status().isOk()));
        draft = body(call(post("/api/v1/supplier-offer-drafts/" + draftId + "/lines/3/exclude"), owner,
                Map.of("version", 2)).andExpect(status().isOk()));
        draft = body(call(post("/api/v1/supplier-offer-drafts/" + draftId + "/confirm"), owner,
                Map.of("version", 3)).andExpect(status().isOk()));
        call(post("/api/v1/supplier-offer-drafts/" + draftId + "/selections"), owner,
                Map.of("version", 4, "lines", List.of(Map.of("lineNumber", 3, "desiredQuantity", 5))))
                .andExpect(status().isBadRequest());
        call(post("/api/v1/supplier-offer-drafts/" + draftId + "/selections"), owner,
                Map.of("version", 4, "lines", List.of(Map.of("lineNumber", 1, "desiredQuantity", 9))))
                .andExpect(status().isBadRequest());
        call(post("/api/v1/supplier-offer-drafts/" + draftId + "/selections"), owner,
                Map.of("version", 4, "lines", List.of(Map.of("lineNumber", 1, "desiredQuantity", 12,
                        "existingProductId", foreignProduct.get("productId").asText()))))
                .andExpect(status().isNotFound());
        assertThat(products.count()).isEqualTo(productCount);
        assertThat(quotes.count()).isEqualTo(quoteCount);

        JsonNode first = body(call(post("/api/v1/supplier-offer-drafts/" + draftId + "/selections"), owner,
                Map.of("version", 4, "lines", List.of(Map.of("lineNumber", 1, "desiredQuantity", 12))))
                .andExpect(status().isCreated()));
        assertThat(first.at("/lines/0/originalName").asText()).isEqualTo("원본 A");
        assertThat(first.at("/lines/0/desiredQuantity").decimalValue()).isEqualByComparingTo("12");
        assertThat(first.get("quoteStatus").asText()).isEqualTo("DRAFT");
        assertThat(first.get("lines")).hasSize(1);
        assertThat(products.count()).isEqualTo(productCount + 1);
        assertThat(quotes.count()).isEqualTo(quoteCount + 1);
        JsonNode visibleSelections = body(call(get("/api/v1/purchase-selections"), owner, null)
                .andExpect(status().isOk()));
        assertThat(visibleSelections).hasSize(1);
        JsonNode otherSelections = body(call(get("/api/v1/purchase-selections"), other, null)
                .andExpect(status().isOk()));
        assertThat(otherSelections).isEmpty();
        call(get("/api/v1/purchase-selections/" + first.get("selectionId").asText()), other, null)
                .andExpect(status().isNotFound());
        call(post("/api/v1/supplier-offer-drafts/" + draftId + "/selections"), owner,
                Map.of("version", 5, "lines", List.of(Map.of("lineNumber", 1, "desiredQuantity", 12))))
                .andExpect(status().isConflict());

        JsonNode second = body(call(post("/api/v1/supplier-offer-drafts/" + draftId + "/selections"), owner,
                Map.of("version", 5, "lines", List.of(Map.of("lineNumber", 2, "desiredQuantity", 5))))
                .andExpect(status().isCreated()));
        assertThat(second.at("/lines/0/reviewedName").asText()).isEqualTo("확인 B");
        assertThat(products.count()).isEqualTo(productCount + 2);
        assertThat(quotes.count()).isEqualTo(quoteCount + 2);

        String quoteId = first.get("quoteId").asText();
        String productId = first.at("/lines/0/productId").asText();
        JsonNode received = body(call(post("/api/v1/quotes/" + quoteId + "/status"), owner,
                Map.of("version", 0, "status", "RECEIVED")).andExpect(status().isOk()));
        assertThat(received.get("lines")).hasSize(1);
        JsonNode scenario = body(call(post("/api/v1/cost-scenarios"), owner,
                Map.of("quoteId", quoteId, "productId", productId, "scenarioName", "선택 A 기준안",
                        "orderQuantity", 12, "excludedQuantity", 0, "exchangeRate", 9,
                        "exchangeRateSource", "USER_ASSUMPTION", "vatTreatment", "RECOVERABLE_EXCLUDED"))
                .andExpect(status().isCreated()));
        assertThat(scenario.get("productId").asText()).isEqualTo(productId);
        assertThat(scenario.get("orderQuantity").decimalValue()).isEqualByComparingTo("12");
    }

    private String member(OrganizationMember.Role role) {
        Organization organization = organizations.save(new Organization(
                "제안 조직 " + UUID.randomUUID(), Organization.Type.SHIPPER, null, null, null));
        AppUser user = users.save(AppUser.registered(
                UUID.randomUUID() + "@example.test", passwords.encode("test-password-1234"), role.name(), null));
        members.save(new OrganizationMember(organization, user, role));
        return auth.login(new LoginRequest(user.getEmail(), "test-password-1234", null)).response().accessToken();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String bearer, Object data) throws Exception {
        request.header("Authorization", "Bearer " + bearer);
        if (data != null) request.contentType("application/json").content(json.writeValueAsString(data));
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}
