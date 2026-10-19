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
import com.tradeoperationsplatform.apiserver.storage.ArtifactObjectStore;
import com.tradeoperationsplatform.apiserver.storage.ObjectStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SupplierDocumentIntegrationTest.Fakes.class)
class SupplierDocumentIntegrationTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired OrganizationRepository organizations;
    @Autowired OrganizationMemberRepository members;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired FakeObjectStore store;
    @Autowired FakeExtractionGateway extraction;

    String owner;
    String other;

    @BeforeEach void setup() {
        store.objects.clear();
        extraction.fail = false;
        extraction.text = "JP-001 스테인리스 텀블러";
        owner = member(OrganizationMember.Role.OWNER);
        other = member(OrganizationMember.Role.OWNER);
    }

    @Test
    void reportsDocumentIntegrationCapabilities() throws Exception {
        JsonNode status = body(mvc.perform(get("/api/v1/supplier-documents/status")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()));

        assertThat(status.get("storageEnabled").asBoolean()).isTrue();
        assertThat(status.get("extractionEnabled").asBoolean()).isTrue();
    }

    @Test
    void archivesExtractsDeduplicatesAndDownloadsOriginalWithoutExposingObjectKey() throws Exception {
        JsonNode supplier = createSupplier(owner);
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-text.pdf"));
        MockMultipartFile file = new MockMultipartFile("file", "quotation.pdf", "application/pdf", pdf);

        JsonNode created = body(mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(file).param("supplierId", supplier.get("businessPartnerId").asText())
                        .param("sourceReference", "email://synthetic-test")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));

        assertThat(created.get("sourceType").asText()).isEqualTo("PDF");
        assertThat(created.get("storageBackend").asText()).isEqualTo("S3");
        assertThat(created.get("extractionMethod").asText()).isEqualTo("PDF_TEXT");
        assertThat(created.get("extractionStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(created.get("extractedText").asText()).contains("JP-001");
        assertThat(created.has("objectKey")).isFalse();
        assertThat(store.objects).hasSize(1);

        JsonNode repeated = body(mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(file).param("supplierId", supplier.get("businessPartnerId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));
        assertThat(repeated.get("documentId").asText()).isEqualTo(created.get("documentId").asText());
        assertThat(store.objects).hasSize(1);

        byte[] downloaded = mvc.perform(get("/api/v1/supplier-documents/" + created.get("documentId").asText() + "/content")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(pdf);
        mvc.perform(get("/api/v1/supplier-documents/" + created.get("documentId").asText())
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void extractedDocumentBecomesReviewDraftBeforePurchaseSelection() throws Exception {
        JsonNode supplier = createSupplier(owner);
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-text.pdf"));
        JsonNode document = body(mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(new MockMultipartFile("file", "quotation.pdf", "application/pdf", pdf))
                        .param("supplierId", supplier.get("businessPartnerId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));
        String path = "/api/v1/supplier-documents/" + document.get("documentId").asText() + "/draft";

        mvc.perform(post(path).header("Authorization", "Bearer " + other)
                        .contentType("application/json").content("{\"currency\":\"JPY\"}"))
                .andExpect(status().isNotFound());
        JsonNode draft = body(mvc.perform(post(path).header("Authorization", "Bearer " + owner)
                        .contentType("application/json").content("{\"currency\":\"jpy\"}"))
                .andExpect(status().isCreated()));
        String draftId = draft.get("draftId").asText();
        assertThat(draft.get("sourceArtifactId").asText()).isEqualTo(document.get("documentId").asText());
        assertThat(draft.get("sourceType").asText()).isEqualTo("PDF");
        assertThat(draft.get("extractedText").asText()).contains("JP-001");
        assertThat(draft.get("currency").asText()).isEqualTo("JPY");
        assertThat(draft.at("/lines/0/originalName").asText()).isEqualTo("JP-001 스테인리스 텀블러");
        assertThat(draft.at("/lines/0/sourceLocation").asText()).isEqualTo("텍스트 1행");
        assertThat(draft.at("/lines/0/status").asText()).isEqualTo("PENDING");
        assertThat(draft.at("/lines/0/unitPrice").isNull()).isTrue();

        JsonNode repeated = body(mvc.perform(post(path).header("Authorization", "Bearer " + owner)
                        .contentType("application/json").content("{\"currency\":\"USD\"}"))
                .andExpect(status().isCreated()));
        assertThat(repeated.get("draftId").asText()).isEqualTo(draftId);
        assertThat(repeated.get("currency").asText()).isEqualTo("JPY");

        mvc.perform(post("/api/v1/supplier-offer-drafts/" + draftId + "/confirm")
                        .header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest());
        draft = body(mvc.perform(put("/api/v1/supplier-offer-drafts/" + draftId + "/lines/1")
                        .header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":0,\"reviewedName\":\"스테인리스 텀블러\",\"supplierSku\":\"JP-001\",\"quantityUnit\":\"EA\",\"minimumQuantity\":10,\"unitPrice\":280}"))
                .andExpect(status().isOk()));
        draft = body(mvc.perform(post("/api/v1/supplier-offer-drafts/" + draftId + "/confirm")
                        .header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":" + draft.get("version").asLong() + "}"))
                .andExpect(status().isOk()));
        assertThat(draft.get("status").asText()).isEqualTo("CONFIRMED");
        JsonNode selection = body(mvc.perform(post("/api/v1/supplier-offer-drafts/" + draftId + "/selections")
                        .header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":" + draft.get("version").asLong()
                                + ",\"lines\":[{\"lineNumber\":1,\"desiredQuantity\":10}]}"))
                .andExpect(status().isCreated()));
        assertThat(selection.at("/lines/0/reviewedName").asText()).isEqualTo("스테인리스 텀블러");
    }

    @Test
    void renderedPdfTableValuesRemainPendingUntilEachCandidateIsReviewed() throws Exception {
        extraction.text = Files.readString(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-layout.txt"));
        JsonNode supplier = createSupplier(owner);
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-text.pdf"));
        JsonNode document = body(mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(new MockMultipartFile("file", "quotation.pdf", "application/pdf", pdf))
                        .param("supplierId", supplier.get("businessPartnerId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));
        JsonNode draft = body(mvc.perform(post("/api/v1/supplier-documents/"
                        + document.get("documentId").asText() + "/draft")
                        .header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"currency\":\"JPY\"}"))
                .andExpect(status().isCreated()));

        JsonNode lines = draft.get("lines");
        assertThat(lines.size()).isGreaterThan(4);
        JsonNode umbrella = null;
        for (JsonNode line : lines) {
            if ("JP-003".equals(line.path("supplierSku").asText())) umbrella = line;
        }
        assertThat(umbrella).isNotNull();
        assertThat(umbrella.get("reviewedName").asText()).isEqualTo("휴대용 접이식 우산");
        assertThat(umbrella.get("unitPrice").decimalValue()).isEqualByComparingTo("1280");
        assertThat(umbrella.get("sourceLocation").asText()).isEqualTo("텍스트 17행");
        assertThat(umbrella.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void reviewsDocumentRowsAtomicallyAndRequiresEveryDecision() throws Exception {
        extraction.text = "JP-001 텀블러 10 EA 280 JP\n배송 조건";
        JsonNode supplier = createSupplier(owner);
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-text.pdf"));
        JsonNode document = body(mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(new MockMultipartFile("file", "batch.pdf", "application/pdf", pdf))
                        .param("supplierId", supplier.get("businessPartnerId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));
        JsonNode draft = body(mvc.perform(post("/api/v1/supplier-documents/"
                        + document.get("documentId").asText() + "/draft")
                        .header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"currency\":\"JPY\"}"))
                .andExpect(status().isCreated()));
        String path = "/api/v1/supplier-offer-drafts/" + draft.get("draftId").asText() + "/review";
        assertThat(draft.at("/lines/0/extractionConfidence").decimalValue()).isEqualByComparingTo("100.00");

        mvc.perform(post(path).header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":0,\"lines\":[{\"lineNumber\":1,\"decision\":\"CONFIRM\","
                                + "\"reviewedName\":\"텀블러\",\"minimumQuantity\":24,\"quantityUnit\":\"EA\"}]}"))
                .andExpect(status().isBadRequest());
        draft = body(mvc.perform(get("/api/v1/supplier-offer-drafts/" + draft.get("draftId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()));
        assertThat(draft.at("/lines/0/status").asText()).isEqualTo("PENDING");

        mvc.perform(post(path).header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":0,\"lines\":[{\"lineNumber\":1,\"decision\":\"CONFIRM\","
                                + "\"reviewedName\":\"텀블러\",\"minimumQuantity\":24,\"quantityUnit\":\"EA\"},"
                                + "{\"lineNumber\":2,\"decision\":\"CONFIRM\",\"reviewedName\":\"\"}]}"))
                .andExpect(status().isBadRequest());
        draft = body(mvc.perform(get("/api/v1/supplier-offer-drafts/" + draft.get("draftId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()));
        assertThat(draft.at("/lines/0/status").asText()).isEqualTo("PENDING");

        draft = body(mvc.perform(post(path).header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":0,\"lines\":[{\"lineNumber\":1,\"decision\":\"CONFIRM\","
                                + "\"reviewedName\":\"텀블러\",\"minimumQuantity\":24,\"quantityUnit\":\"EA\","
                                + "\"unitPrice\":280},{\"lineNumber\":2,\"decision\":\"EXCLUDE\"}]}"))
                .andExpect(status().isOk()));
        assertThat(draft.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(draft.at("/lines/0/minimumQuantity").decimalValue()).isEqualByComparingTo("24");
        assertThat(draft.at("/lines/1/status").asText()).isEqualTo("EXCLUDED");
    }

    @Test
    void failedExtractionCannotBecomeDraft() throws Exception {
        extraction.fail = true;
        JsonNode supplier = createSupplier(owner);
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-blurred-scan.pdf"));
        JsonNode document = body(mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(new MockMultipartFile("file", "blurred.pdf", "application/pdf", pdf))
                        .param("supplierId", supplier.get("businessPartnerId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));
        mvc.perform(post("/api/v1/supplier-documents/" + document.get("documentId").asText() + "/draft")
                        .header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"currency\":\"JPY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retriesFailedExtractionFromArchivedOriginalAndPreservesAttemptHistory() throws Exception {
        extraction.fail = true;
        JsonNode supplier = createSupplier(owner);
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-text.pdf"));
        JsonNode document = body(mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(new MockMultipartFile("file", "retry.pdf", "application/pdf", pdf))
                        .param("supplierId", supplier.get("businessPartnerId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));
        String documentId = document.get("documentId").asText();
        String retryPath = "/api/v1/supplier-documents/" + documentId + "/extractions/retry";
        String historyPath = "/api/v1/supplier-documents/" + documentId + "/extractions";
        String firstRunId = document.get("extractionRunId").asText();
        assertThat(document.get("extractionStatus").asText()).isEqualTo("FAILED");

        mvc.perform(post(retryPath).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        extraction.fail = false;
        JsonNode retried = body(mvc.perform(post(retryPath).header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));
        assertThat(retried.get("documentId").asText()).isEqualTo(documentId);
        assertThat(retried.get("extractionRunId").asText()).isNotEqualTo(firstRunId);
        assertThat(retried.get("extractionStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(store.objects).hasSize(1);

        JsonNode attempts = body(mvc.perform(get(historyPath).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()));
        assertThat(attempts.size()).isEqualTo(2);
        assertThat(attempts.get(0).get("extractionRunId").asText()).isEqualTo(retried.get("extractionRunId").asText());
        assertThat(attempts.get(0).get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(attempts.get(1).get("extractionRunId").asText()).isEqualTo(firstRunId);
        assertThat(attempts.get(1).get("status").asText()).isEqualTo("FAILED");
        mvc.perform(get(historyPath).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(post(retryPath).header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict());

        JsonNode draft = body(mvc.perform(post("/api/v1/supplier-documents/" + documentId + "/draft")
                        .header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"currency\":\"JPY\"}"))
                .andExpect(status().isCreated()));
        assertThat(draft.get("extractionRunId").asText()).isEqualTo(retried.get("extractionRunId").asText());
    }

    @Test
    void keepsOriginalAndMarksReviewWhenExtractionFails() throws Exception {
        extraction.fail = true;
        JsonNode supplier = createSupplier(owner);
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/fixtures/ocr/supplier-quotation-blurred-scan.pdf"));

        JsonNode created = body(mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(new MockMultipartFile("file", "blurred.pdf", "application/pdf", pdf))
                        .param("supplierId", supplier.get("businessPartnerId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()));

        assertThat(created.get("extractionStatus").asText()).isEqualTo("FAILED");
        assertThat(created.get("reviewRequired").asBoolean()).isTrue();
        assertThat(created.get("errorMessage").asText()).contains("synthetic OCR failure");
        assertThat(store.objects).hasSize(1);
    }

    @Test
    void rejectsExtensionMagicMismatchBeforeStorage() throws Exception {
        JsonNode supplier = createSupplier(owner);
        mvc.perform(multipart("/api/v1/supplier-documents/import")
                        .file(new MockMultipartFile("file", "not-really.pdf", "application/pdf", "nope".getBytes()))
                        .param("supplierId", supplier.get("businessPartnerId").asText())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest());
        assertThat(store.objects).isEmpty();
    }

    private JsonNode createSupplier(String token) throws Exception {
        ResultActions result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/organizations/current/partners")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "문서 공급처 " + UUID.randomUUID(),
                                "roles", java.util.List.of("SUPPLIER")))))
                .andExpect(status().isCreated());
        return body(result);
    }

    private String member(OrganizationMember.Role role) {
        Organization organization = organizations.save(new Organization(
                "문서 조직 " + UUID.randomUUID(), Organization.Type.SHIPPER, null, null, null));
        AppUser user = users.save(AppUser.registered(
                UUID.randomUUID() + "@example.test", passwords.encode("test-password-1234"), role.name(), null));
        members.save(new OrganizationMember(organization, user, role));
        return auth.login(new LoginRequest(user.getEmail(), "test-password-1234", null)).response().accessToken();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean @Primary FakeObjectStore fakeObjectStore() { return new FakeObjectStore(); }
        @Bean @Primary FakeExtractionGateway fakeExtractionGateway() { return new FakeExtractionGateway(); }
    }

    static class FakeObjectStore implements ArtifactObjectStore {
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        @Override public boolean isEnabled() { return true; }
        @Override public StoredObject put(String key, String contentType, byte[] content, String sha256) {
            objects.put(key, content.clone());
            return new StoredObject("S3", "test-documents", key, "test-etag");
        }
        @Override public byte[] get(String key) {
            byte[] value = objects.get(key);
            if (value == null) throw new ObjectStorageException("missing test object");
            return value.clone();
        }
        @Override public void delete(String key) { objects.remove(key); }
    }

    static class FakeExtractionGateway implements DocumentExtractionGateway {
        volatile boolean fail;
        volatile String text = "JP-001 스테인리스 텀블러";
        @Override public boolean isEnabled() { return true; }
        @Override public Result extract(byte[] content, String fileName, String contentType) {
            if (fail) throw new DocumentExtractionException("synthetic OCR failure");
            return new Result("test-extractor-v1", ExtractionRun.Method.PDF_TEXT,
                    text, new BigDecimal("100.00"), 1, false, false,
                    "{\"method\":\"PDF_TEXT\"}");
        }
    }
}
