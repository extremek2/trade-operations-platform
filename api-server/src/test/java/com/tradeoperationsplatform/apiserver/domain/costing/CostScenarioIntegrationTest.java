package com.tradeoperationsplatform.apiserver.domain.costing;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CostScenarioIntegrationTest extends PostgresTestSupport {
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
        ownerToken = createMember("원가 조직", OrganizationMember.Role.OWNER);
        otherToken = createMember("다른 원가 조직", OrganizationMember.Role.OWNER);
    }

    @Test
    void createsImmutableSnapshotWithExplicitSourcesAndVatPerspective() throws Exception {
        Fixture fixture = receivedQuote(ownerToken, "10.00");
        JsonNode scenario = createScenario(ownerToken, fixture, null, "기준 환율");

        assertThat(scenario.get("calculationStatus").asText()).isEqualTo("COMPLETE");
        assertThat(scenario.get("calculationRuleVersion").asText()).isEqualTo("COST_V1");
        assertThat(scenario.get("quotedUnitPriceSource").asText()).isEqualTo("SUPPLIER_QUOTE");
        assertThat(scenario.get("exchangeRateSource").asText()).isEqualTo("USER_ASSUMPTION");
        assertThat(scenario.get("vatTreatment").asText()).isEqualTo("RECOVERABLE_EXCLUDED");
        assertThat(scenario.get("totalCashRequiredKrw").decimalValue()).isEqualByComparingTo("191200.00");
        assertThat(scenario.get("totalLandedCostKrw").decimalValue()).isEqualByComparingTo("175000.00");
        assertThat(scenario.get("unitLandedCostKrw").decimalValue()).isEqualByComparingTo("21875.00");
        assertThat(scenario.get("contributionMarginPerUnitKrw").decimalValue()).isEqualByComparingTo("4125.00");

        JsonNode rows = body(call(get("/api/v1/cost-scenarios"), ownerToken, null).andExpect(status().isOk()));
        assertThat(rows).hasSize(1);
        call(get("/api/v1/cost-scenarios/" + scenario.get("scenarioId").asText()), otherToken, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void scenarioRevisionIsLinearAndNeverOverwritesPriorSnapshot() throws Exception {
        Fixture fixture = receivedQuote(ownerToken, "10.00");
        JsonNode first = createScenario(ownerToken, fixture, null, "환율 1300");
        JsonNode second = createScenario(ownerToken, fixture, first.get("scenarioId").asText(), "환율 변경");

        assertThat(second.get("revisionNumber").asInt()).isEqualTo(2);
        assertThat(second.get("previousScenarioId").asText()).isEqualTo(first.get("scenarioId").asText());
        assertThat(first.get("revisionNumber").asInt()).isEqualTo(1);
        call(post("/api/v1/cost-scenarios"), ownerToken,
                scenarioRequest(fixture, first.get("scenarioId").asText(), "중복 개정"))
                .andExpect(status().isConflict());
    }

    @Test
    void missingSupplierPriceRemainsIncompleteAndDraftQuoteIsRejected() throws Exception {
        Fixture receivedWithoutPrice = receivedQuote(ownerToken, null);
        JsonNode incomplete = createScenario(ownerToken, receivedWithoutPrice, null, "단가 확인 전");
        assertThat(incomplete.get("calculationStatus").asText()).isEqualTo("INCOMPLETE");
        assertThat(incomplete.get("quotedUnitPrice").isNull()).isTrue();
        assertThat(incomplete.get("totalLandedCostKrw").isNull()).isTrue();

        Fixture draft = quote(ownerToken, "5.00", false);
        call(post("/api/v1/cost-scenarios"), ownerToken, scenarioRequest(draft, null, "초안 계산"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizationCannotReuseAnotherOrganizationsQuoteAndViewerCannotCreate() throws Exception {
        Fixture fixture = receivedQuote(ownerToken, "10.00");
        call(post("/api/v1/cost-scenarios"), otherToken, scenarioRequest(fixture, null, "조직 침범"))
                .andExpect(status().isNotFound());

        String viewerToken = createMember("조회 전용 원가 조직", OrganizationMember.Role.VIEWER);
        call(get("/api/v1/cost-scenarios"), viewerToken, null).andExpect(status().isOk());
        call(post("/api/v1/cost-scenarios"), viewerToken, scenarioRequest(fixture, null, "등록 불가"))
                .andExpect(status().isForbidden());
    }

    private JsonNode createScenario(String token, Fixture fixture, String previousId, String name) throws Exception {
        return body(call(post("/api/v1/cost-scenarios"), token, scenarioRequest(fixture, previousId, name))
                .andExpect(status().isCreated()));
    }

    private Map<String, Object> scenarioRequest(Fixture fixture, String previousId, String name) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("quoteId", fixture.quoteId());
        request.put("productId", fixture.productId());
        if (previousId != null) request.put("previousScenarioId", previousId);
        request.put("scenarioName", name);
        request.put("orderQuantity", 10);
        request.put("excludedQuantity", 2);
        request.put("exchangeRate", 1300);
        request.put("exchangeRateSource", "USER_ASSUMPTION");
        request.put("taxableFreightKrw", 20000);
        request.put("customsClearanceCostKrw", 5000);
        request.put("inspectionCostKrw", 0);
        request.put("warehouseCostKrw", 3000);
        request.put("domesticDeliveryCostKrw", 4000);
        request.put("otherCostKrw", 1000);
        request.put("logisticsCostSource", "USER_ASSUMPTION");
        request.put("customsDutyRate", 8);
        request.put("vatRate", 10);
        request.put("taxRateSource", "USER_ASSUMPTION");
        request.put("vatTreatment", "RECOVERABLE_EXCLUDED");
        request.put("targetSellingPriceKrw", 30000);
        request.put("sellingFeeRate", 10);
        request.put("variableCostPerUnitKrw", 1000);
        request.put("salesAssumptionSource", "USER_ASSUMPTION");
        return request;
    }

    private Fixture receivedQuote(String token, String unitPrice) throws Exception {
        return quote(token, unitPrice, true);
    }

    private Fixture quote(String token, String unitPrice, boolean receive) throws Exception {
        JsonNode product = body(call(post("/api/v1/products"), token,
                Map.of("name", "원가 상품 " + UUID.randomUUID(), "hypothesis", "예상 원가 검증"))
                .andExpect(status().isCreated()));
        String productId = product.get("productId").asText();
        body(call(post("/api/v1/products/" + productId + "/status"), token,
                Map.of("status", "SOURCING", "reason", "견적 요청", "version", 0))
                .andExpect(status().isOk()));
        JsonNode supplier = body(call(post("/api/v1/organizations/current/partners"), token,
                Map.of("name", "원가 공급사 " + UUID.randomUUID(), "roles", List.of("SUPPLIER")))
                .andExpect(status().isCreated()));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("productId", productId);
        line.put("description", "원가 계산 견적");
        line.put("minimumQuantity", 10);
        line.put("quantityUnit", "EA");
        if (unitPrice != null) line.put("unitPrice", unitPrice);
        JsonNode quote = body(call(post("/api/v1/quotes"), token, Map.of(
                "supplierId", supplier.get("businessPartnerId").asText(),
                "quoteNumber", "COST-" + UUID.randomUUID(), "currency", "USD", "lines", new Object[]{line}))
                .andExpect(status().isCreated()));
        if (receive) {
            quote = body(call(post("/api/v1/quotes/" + quote.get("quoteId").asText() + "/status"), token,
                    Map.of("status", "RECEIVED", "version", 0)).andExpect(status().isOk()));
        }
        return new Fixture(productId, quote.get("quoteId").asText());
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

    private record Fixture(String productId, String quoteId) {}
}
