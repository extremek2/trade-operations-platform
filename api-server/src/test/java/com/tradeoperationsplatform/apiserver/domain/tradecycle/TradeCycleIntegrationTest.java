package com.tradeoperationsplatform.apiserver.domain.tradecycle;

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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TradeCycleIntegrationTest extends PostgresTestSupport {
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
        ownerToken = createMember("수입 사이클 조직", OrganizationMember.Role.OWNER);
        otherToken = createMember("다른 사이클 조직", OrganizationMember.Role.OWNER);
    }

    @Test
    void selectedScenarioFlowsThroughPartialShipmentReceiptCostSalesAndDecision() throws Exception {
        JsonNode scenario = selectedScenario(ownerToken);
        JsonNode cycle = body(call(post("/api/v1/trade-cycles/purchase-orders"), ownerToken, Map.of(
                "costScenarioId", scenario.get("scenarioId").asText(), "orderNumber", "PO-" + UUID.randomUUID()))
                .andExpect(status().isCreated()));
        String orderId = cycle.at("/purchaseOrder/purchaseOrderId").asText();
        String lineId = cycle.at("/purchaseOrder/lines/0/purchaseOrderLineId").asText();
        assertThat(cycle.at("/purchaseOrder/status").asText()).isEqualTo("DRAFT");

        JsonNode approved = body(call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/approve"), ownerToken,
                Map.of("version", 0)).andExpect(status().isOk()));
        assertThat(approved.at("/purchaseOrder/status").asText()).isEqualTo("APPROVED");
        call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/payment"), ownerToken, Map.of(
                "version", approved.at("/purchaseOrder/version").asLong(), "paymentStatus", "PAID",
                "paidAmount", 100000, "paidAt", "2026-09-15", "evidenceReference", "BANK-001"))
                .andExpect(status().isOk());

        JsonNode firstShipment = shipment(ownerToken, "SHIP-A-");
        JsonNode secondShipment = shipment(ownerToken, "SHIP-B-");
        JsonNode partial = body(call(post("/api/v1/trade-cycles/shipment-allocations"), ownerToken, Map.of(
                "shipmentId", firstShipment.get("shipmentId").asText(), "purchaseOrderLineId", lineId, "quantity", 6))
                .andExpect(status().isCreated()));
        String firstAllocation = partial.at("/shipmentAllocations/0/allocationId").asText();
        assertThat(partial.at("/purchaseOrder/status").asText()).isEqualTo("PARTIALLY_SHIPPED");
        JsonNode shipped = body(call(post("/api/v1/trade-cycles/shipment-allocations"), ownerToken, Map.of(
                "shipmentId", secondShipment.get("shipmentId").asText(), "purchaseOrderLineId", lineId, "quantity", 4))
                .andExpect(status().isCreated()));
        String secondAllocation = shipped.at("/shipmentAllocations/1/allocationId").asText();
        assertThat(shipped.at("/purchaseOrder/status").asText()).isEqualTo("SHIPPED");

        JsonNode firstReceipt = receive(ownerToken, firstShipment.get("shipmentId").asText(), firstAllocation, 4, "LOT-A-");
        assertThat(firstReceipt.at("/purchaseOrder/status").asText()).isEqualTo("SHIPPED");
        JsonNode secondReceipt = receive(ownerToken, firstShipment.get("shipmentId").asText(), firstAllocation, 2, "LOT-B-");
        assertThat(secondReceipt.at("/purchaseOrder/status").asText()).isEqualTo("SHIPPED");
        JsonNode completed = receive(ownerToken, secondShipment.get("shipmentId").asText(), secondAllocation, 4, "LOT-C-");
        assertThat(completed.at("/purchaseOrder/status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.at("/purchaseOrder/lines/0/receivedQuantity").decimalValue()).isEqualByComparingTo("10");

        body(call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/actual-costs"), ownerToken, Map.of(
                "costType", "PRODUCT", "description", "실제 상품대", "amountKrw", 100000))
                .andExpect(status().isCreated()));
        body(call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/actual-costs"), ownerToken, Map.of(
                "costType", "FREIGHT", "description", "실제 국제운임", "amountKrw", "20000.01"))
                .andExpect(status().isCreated()));
        JsonNode closed = body(call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/cost-close"), ownerToken, null)
                .andExpect(status().isOk()));
        assertThat(closed.at("/costClose/actualTotalKrw").decimalValue()).isEqualByComparingTo("120000.01");
        assertThat(closed.at("/costClose/varianceKrw").decimalValue()).isEqualByComparingTo("10000.01");
        BigDecimal allocated = BigDecimal.ZERO;
        for (JsonNode allocation : closed.at("/costClose/allocations")) allocated = allocated.add(allocation.get("allocatedAmountKrw").decimalValue());
        assertThat(allocated).isEqualByComparingTo("120000.01");

        String lotId = closed.at("/inventoryLots/0/inventoryLotId").asText();
        JsonNode sold = body(call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/sales"), ownerToken, Map.of(
                "inventoryLotId", lotId, "channel", "SMARTSTORE", "soldQuantity", 2, "returnedQuantity", 0,
                "grossRevenueKrw", 50000, "channelCostKrw", 5000)).andExpect(status().isCreated()));
        assertThat(sold.at("/inventoryLots/0/sellableQuantity").decimalValue()).isEqualByComparingTo("2");
        JsonNode decided = body(call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/decisions"), ownerToken,
                Map.of("decision", "REORDER", "reason", "실제 원가와 초기 판매 반응이 목표 범위입니다."))
                .andExpect(status().isCreated()));
        assertThat(decided.at("/decisions/0/decision").asText()).isEqualTo("REORDER");

        call(get("/api/v1/trade-cycles/purchase-orders/" + orderId), otherToken, null).andExpect(status().isNotFound());
    }

    @Test
    void boundariesRejectUnselectedScenarioOverAllocationAndStaleVersion() throws Exception {
        JsonNode scenario = scenario(ownerToken, false);
        call(post("/api/v1/trade-cycles/purchase-orders"), ownerToken, Map.of(
                "costScenarioId", scenario.get("scenarioId").asText(), "orderNumber", "PO-UNSELECTED-" + UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        JsonNode selected = selectedScenario(ownerToken);
        JsonNode cycle = body(call(post("/api/v1/trade-cycles/purchase-orders"), ownerToken, Map.of(
                "costScenarioId", selected.get("scenarioId").asText(), "orderNumber", "PO-BOUNDARY-" + UUID.randomUUID()))
                .andExpect(status().isCreated()));
        String orderId = cycle.at("/purchaseOrder/purchaseOrderId").asText();
        String lineId = cycle.at("/purchaseOrder/lines/0/purchaseOrderLineId").asText();
        call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/approve"), ownerToken, Map.of("version", 99))
                .andExpect(status().isConflict());
        body(call(post("/api/v1/trade-cycles/purchase-orders/" + orderId + "/approve"), ownerToken, Map.of("version", 0))
                .andExpect(status().isOk()));
        JsonNode shipment = shipment(ownerToken, "OVER-");
        call(post("/api/v1/trade-cycles/shipment-allocations"), ownerToken, Map.of(
                "shipmentId", shipment.get("shipmentId").asText(), "purchaseOrderLineId", lineId, "quantity", 11))
                .andExpect(status().isBadRequest());
    }

    private JsonNode receive(String token, String shipmentId, String allocationId, int quantity, String lotPrefix) throws Exception {
        return body(call(post("/api/v1/trade-cycles/receipts"), token, Map.of(
                "shipmentId", shipmentId, "receiptNumber", "RCV-" + UUID.randomUUID(),
                "lines", List.of(Map.of("allocationId", allocationId, "quantity", quantity,
                        "lotNumber", lotPrefix + UUID.randomUUID())))).andExpect(status().isCreated()));
    }

    private JsonNode shipment(String token, String prefix) throws Exception {
        return body(call(post("/api/v1/shipments"), token, Map.of(
                "caseNumber", prefix + UUID.randomUUID(), "direction", "IMPORT", "transportMode", "SEA"))
                .andExpect(status().isCreated()));
    }

    private JsonNode selectedScenario(String token) throws Exception { return scenario(token, true); }

    private JsonNode scenario(String token, boolean select) throws Exception {
        JsonNode product = body(call(post("/api/v1/products"), token,
                Map.of("name", "사이클 상품 " + UUID.randomUUID(), "hypothesis", "한 사이클 검증"))
                .andExpect(status().isCreated()));
        String productId = product.get("productId").asText();
        body(call(post("/api/v1/products/" + productId + "/status"), token,
                Map.of("status", "SOURCING", "reason", "견적 요청", "version", 0)).andExpect(status().isOk()));
        JsonNode supplier = body(call(post("/api/v1/organizations/current/partners"), token,
                Map.of("name", "사이클 공급사 " + UUID.randomUUID(), "roles", List.of("SUPPLIER")))
                .andExpect(status().isCreated()));
        JsonNode quote = body(call(post("/api/v1/quotes"), token, Map.of(
                "supplierId", supplier.get("businessPartnerId").asText(), "quoteNumber", "Q-" + UUID.randomUUID(),
                "currency", "KRW", "lines", List.of(Map.of("productId", productId, "description", "발주 대상",
                        "minimumQuantity", 10, "quantityUnit", "EA", "unitPrice", 10000))))
                .andExpect(status().isCreated()));
        String quoteId = quote.get("quoteId").asText();
        body(call(post("/api/v1/quotes/" + quoteId + "/status"), token,
                Map.of("status", "RECEIVED", "version", 0)).andExpect(status().isOk()));
        Map<String, Object> scenarioRequest = new LinkedHashMap<>();
        scenarioRequest.put("quoteId", quoteId); scenarioRequest.put("productId", productId);
        scenarioRequest.put("scenarioName", "발주 기준안"); scenarioRequest.put("orderQuantity", 10);
        scenarioRequest.put("excludedQuantity", 0); scenarioRequest.put("exchangeRate", 1);
        scenarioRequest.put("exchangeRateSource", "USER_ASSUMPTION"); scenarioRequest.put("taxableFreightKrw", 10000);
        scenarioRequest.put("customsClearanceCostKrw", 0); scenarioRequest.put("inspectionCostKrw", 0);
        scenarioRequest.put("warehouseCostKrw", 0); scenarioRequest.put("domesticDeliveryCostKrw", 0);
        scenarioRequest.put("otherCostKrw", 0);
        scenarioRequest.put("logisticsCostSource", "USER_ASSUMPTION"); scenarioRequest.put("customsDutyRate", 0);
        scenarioRequest.put("vatRate", 10); scenarioRequest.put("taxRateSource", "USER_ASSUMPTION");
        scenarioRequest.put("vatTreatment", "RECOVERABLE_EXCLUDED"); scenarioRequest.put("targetSellingPriceKrw", 25000);
        scenarioRequest.put("sellingFeeRate", 10); scenarioRequest.put("variableCostPerUnitKrw", 1000);
        scenarioRequest.put("salesAssumptionSource", "USER_ASSUMPTION");
        JsonNode scenario = body(call(post("/api/v1/cost-scenarios"), token, scenarioRequest).andExpect(status().isCreated()));
        if (select) body(call(post("/api/v1/quotes/" + quoteId + "/status"), token,
                Map.of("status", "SELECTED", "version", 1)).andExpect(status().isOk()));
        return scenario;
    }

    private String createMember(String organizationName, OrganizationMember.Role role) {
        Organization organization = organizations.save(new Organization(
                organizationName + " " + UUID.randomUUID(), Organization.Type.SHIPPER, null, null, null));
        AppUser user = users.save(AppUser.registered(UUID.randomUUID() + "@example.test", passwords.encode(PASSWORD), role.name(), null));
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
