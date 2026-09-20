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
        owner = member(OrganizationMember.Role.OWNER);
        other = member(OrganizationMember.Role.OWNER);
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
        @Override public boolean isEnabled() { return true; }
        @Override public Result extract(byte[] content, String fileName, String contentType) {
            if (fail) throw new DocumentExtractionException("synthetic OCR failure");
            return new Result("test-extractor-v1", ExtractionRun.Method.PDF_TEXT,
                    "JP-001 스테인리스 텀블러", new BigDecimal("100.00"), 1, false, false,
                    "{\"method\":\"PDF_TEXT\"}");
        }
    }
}
