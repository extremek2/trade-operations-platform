package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import com.tradeoperationsplatform.apiserver.domain.partner.repository.BusinessPartnerRepository;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import com.tradeoperationsplatform.apiserver.global.exception.ConflictException;
import com.tradeoperationsplatform.apiserver.global.exception.ResourceNotFoundException;
import com.tradeoperationsplatform.apiserver.storage.ArtifactObjectStore;
import com.tradeoperationsplatform.apiserver.storage.ObjectStorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierDocumentService {
    static final int MAX_BYTES = 15 * 1024 * 1024;
    private static final Set<SourceArtifact.SourceType> DOCUMENT_TYPES =
            Set.of(SourceArtifact.SourceType.PDF, SourceArtifact.SourceType.IMAGE);

    private final SourceArtifactRepository artifacts;
    private final ExtractionRunRepository runs;
    private final BusinessPartnerRepository partners;
    private final CurrentActor currentActor;
    private final ArtifactObjectStore objectStore;
    private final DocumentExtractionGateway extractionGateway;

    @Transactional
    public SupplierDocumentResponse importDocument(UUID supplierId, String sourceReference, MultipartFile file) {
        var actor = currentActor.require();
        BusinessPartner supplier = requireSupplier(supplierId, actor.organization().getId());
        if (sourceReference != null && sourceReference.length() > 2000)
            throw new BusinessException("원본 위치는 2000자를 초과할 수 없습니다.");
        if (!objectStore.isEnabled())
            throw unavailable("문서 저장소가 비활성화되어 있습니다.");
        String fileName = safeFileName(file == null ? null : file.getOriginalFilename());
        byte[] content = read(file);
        DocumentType document = detect(fileName, content);
        String hash = sha256(content);
        Optional<SourceArtifact> existing = artifacts.findByOrganizationIdAndSupplierIdAndContentHash(
                actor.organization().getId(), supplier.getId(), hash);
        if (existing.isPresent()) {
            if (!DOCUMENT_TYPES.contains(existing.get().getSourceType()))
                throw new ConflictException("같은 내용이 다른 원본 형식으로 이미 등록되었습니다.");
            return response(existing.get());
        }

        String key = "organizations/" + actor.organization().getPublicId()
                + "/supplier-documents/" + UUID.randomUUID() + "/original";
        ArtifactObjectStore.StoredObject stored;
        try {
            stored = objectStore.put(key, document.contentType(), content, hash);
        } catch (ObjectStorageException e) {
            throw unavailable(e.getMessage());
        }
        try {
            SourceArtifact artifact = artifacts.saveAndFlush(new SourceArtifact(actor.organization(), supplier,
                    actor.user(), document.sourceType(), sourceReference, fileName, document.contentType(),
                    content.length, hash, stored.backend(), stored.bucket(), stored.key(), stored.etag()));
            ExtractionRun run = runs.saveAndFlush(new ExtractionRun(artifact, true));
            run.start();
            runs.flush();
            if (!extractionGateway.isEnabled()) {
                run.fail("OCR 연결이 비활성화되어 원본만 보관했습니다.");
            } else {
                try {
                    DocumentExtractionGateway.Result result = extractionGateway.extract(content, fileName, document.contentType());
                    run.succeed(result.method(), result.engineVersion(), result.text(), result.confidence(),
                            result.pageCount(), result.preprocessingApplied(), result.reviewRequired(), result.rawPayload());
                } catch (DocumentExtractionException e) {
                    run.fail(limit(e.getMessage(), 2000));
                }
            }
            runs.flush();
            return SupplierDocumentResponse.from(artifact, run);
        } catch (DataIntegrityViolationException e) {
            safeDelete(stored.key());
            throw new ConflictException("이 공급처의 동일한 문서가 이미 등록되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            safeDelete(stored.key());
            throw new BusinessException(e.getMessage());
        } catch (RuntimeException e) {
            safeDelete(stored.key());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<SupplierDocumentResponse> findAll() {
        Long organizationId = currentActor.require().organization().getId();
        return artifacts.findAllByOrganizationIdAndSourceTypeInOrderByReceivedAtDesc(organizationId, DOCUMENT_TYPES)
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public SupplierDocumentResponse findOne(UUID documentId) { return response(require(documentId)); }

    @Transactional(readOnly = true)
    public Download download(UUID documentId) {
        SourceArtifact artifact = require(documentId);
        if (!objectStore.isEnabled()) throw unavailable("문서 저장소가 비활성화되어 있습니다.");
        final byte[] content;
        try { content = objectStore.get(artifact.getObjectKey()); }
        catch (ObjectStorageException e) { throw unavailable(e.getMessage()); }
        if (content.length != artifact.getFileSize() || !sha256(content).equals(artifact.getContentHash()))
            throw unavailable("보관된 문서의 무결성 검증에 실패했습니다.");
        return new Download(artifact.getOriginalFileName(), artifact.getContentType(), content);
    }

    private SupplierDocumentResponse response(SourceArtifact artifact) {
        ExtractionRun run = runs.findTopBySourceArtifactIdOrderByCreatedAtDesc(artifact.getId())
                .orElseThrow(() -> new ConflictException("문서에 연결된 추출 이력을 찾을 수 없습니다."));
        return SupplierDocumentResponse.from(artifact, run);
    }

    private SourceArtifact require(UUID documentId) {
        Long organizationId = currentActor.require().organization().getId();
        SourceArtifact artifact = artifacts.findByPublicIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("공급 문서를 찾을 수 없습니다."));
        if (!DOCUMENT_TYPES.contains(artifact.getSourceType()))
            throw new ResourceNotFoundException("공급 문서를 찾을 수 없습니다.");
        return artifact;
    }

    private BusinessPartner requireSupplier(UUID supplierId, Long organizationId) {
        BusinessPartner supplier = partners.findOwnedWithRoles(supplierId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("공급처를 찾을 수 없습니다."));
        if (!supplier.hasRole(BusinessPartner.Role.SUPPLIER) || supplier.getStatus() != BusinessPartner.Status.ACTIVE)
            throw new BusinessException("활성 공급처의 문서만 등록할 수 있습니다.");
        return supplier;
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("비어 있는 문서는 등록할 수 없습니다.");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "문서는 15MB 이하여야 합니다.");
        try { return file.getBytes(); }
        catch (IOException e) { throw new BusinessException("문서 원본을 읽을 수 없습니다."); }
    }

    private DocumentType detect(String fileName, byte[] content) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (content.length >= 5 && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
                && content[3] == 'F' && content[4] == '-' && lower.endsWith(".pdf"))
            return new DocumentType(SourceArtifact.SourceType.PDF, "application/pdf");
        if (content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 'P' && content[2] == 'N'
                && content[3] == 'G' && lower.endsWith(".png"))
            return new DocumentType(SourceArtifact.SourceType.IMAGE, "image/png");
        if (content.length >= 3 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8
                && content[2] == (byte) 0xff && (lower.endsWith(".jpg") || lower.endsWith(".jpeg")))
            return new DocumentType(SourceArtifact.SourceType.IMAGE, "image/jpeg");
        throw new BusinessException("확장자와 실제 형식이 일치하는 PDF, PNG, JPEG만 등록할 수 있습니다.");
    }

    private String safeFileName(String original) {
        if (original == null || original.isBlank()) throw new BusinessException("원본 파일명이 필요합니다.");
        try {
            String name = Path.of(original.replace('\\', '/')).getFileName().toString().trim();
            if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl))
                throw new BusinessException("원본 파일명이 올바르지 않습니다.");
            return name;
        } catch (InvalidPathException e) {
            throw new BusinessException("원본 파일명이 올바르지 않습니다.");
        }
    }

    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e); }
    }
    private void safeDelete(String key) {
        try { objectStore.delete(key); } catch (RuntimeException ignored) {}
    }
    private String limit(String value, int max) {
        String message = value == null || value.isBlank() ? "문서 추출에 실패했습니다." : value;
        return message.substring(0, Math.min(max, message.length()));
    }
    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private record DocumentType(SourceArtifact.SourceType sourceType, String contentType) {}
    public record Download(String fileName, String contentType, byte[] content) {}
}
