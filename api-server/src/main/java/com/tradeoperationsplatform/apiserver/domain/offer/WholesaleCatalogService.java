package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import com.tradeoperationsplatform.apiserver.domain.partner.repository.BusinessPartnerRepository;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.IdentityAuditEvent;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.IdentityAuditRepository;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import com.tradeoperationsplatform.apiserver.global.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WholesaleCatalogService {
    private final DomeggookCatalogGateway gateway;
    private final BusinessPartnerRepository partners;
    private final IdentityAuditRepository audit;
    private final SourceArtifactRepository artifacts;
    private final ExtractionRunRepository runs;
    private final SupplierOfferDraftRepository drafts;
    private final CurrentActor currentActor;

    public record Status(boolean liveConfigured, String platform) {}

    public Status status() { currentActor.require(); return new Status(gateway.liveConfigured(), "DOMEGGOOK"); }
    public List<WholesaleCatalogItem> search(String keyword, DomeggookCatalogGateway.Mode mode) {
        currentActor.require();
        return gateway.search(keyword, mode);
    }
    public WholesaleCatalogItem detail(String itemId, DomeggookCatalogGateway.Mode mode) {
        currentActor.require();
        return gateway.detail(itemId, mode).item();
    }

    @Transactional
    public SupplierOfferResponse importItem(String itemId, DomeggookCatalogGateway.Mode mode) {
        var actor = currentActor.require();
        var snapshot = gateway.detail(itemId, mode);
        WholesaleCatalogItem item = snapshot.item();
        if (item.overseasShipping())
            throw new BusinessException("해외직배송 상품은 국내 도매 매입 실증 대상에서 제외됩니다.");
        if (item.sellerId() == null || item.sellerId().isBlank())
            throw new BusinessException("판매자 식별정보가 없어 제안을 등록할 수 없습니다.");
        String partnerName = mode == DomeggookCatalogGateway.Mode.SAMPLE
                ? "샘플 도매꾹 판매자 " + item.sellerId()
                : "도매꾹 판매자 " + item.sellerId();
        if (partnerName.length() > 255) throw new BusinessException("판매자 식별정보가 너무 깁니다.");
        BusinessPartner supplier = partners.findOwnedByName(actor.organization().getId(), partnerName)
                .orElseGet(() -> {
                    BusinessPartner created = partners.saveAndFlush(new BusinessPartner(actor.organization(),
                            partnerName, null, null, BusinessPartner.Role.SUPPLIER));
                    audit.save(IdentityAuditEvent.businessPartner(actor.user(), created));
                    return created;
                });
        if (!supplier.hasRole(BusinessPartner.Role.SUPPLIER) || supplier.getStatus() != BusinessPartner.Status.ACTIVE)
            throw new BusinessException("활성 공급처만 제안을 등록할 수 있습니다.");
        String hash = sha256(snapshot.raw());
        var existing = artifacts.findByOrganizationIdAndSupplierIdAndContentHash(
                actor.organization().getId(), supplier.getId(), hash);
        if (existing.isPresent()) return drafts.findByExtractionRunSourceArtifactId(existing.get().getId())
                .map(SupplierOfferResponse::from)
                .orElseThrow(() -> new ConflictException("동일한 API 원본의 검토 초안을 찾을 수 없습니다."));
        try {
            String sourceReference = item.productUrl() == null ? "sample://domeggook/" + item.itemId() : item.productUrl();
            SourceArtifact artifact = artifacts.saveAndFlush(new SourceArtifact(actor.organization(), supplier,
                    actor.user(), SourceArtifact.SourceType.API, sourceReference, snapshot.raw(), hash));
            ExtractionRun run = runs.saveAndFlush(new ExtractionRun(artifact, ExtractionRun.Method.API,
                    "domeggook-xml-v1"));
            SupplierOfferDraft draft = new SupplierOfferDraft(actor.organization(), supplier, run, actor.user(), "KRW");
            SupplierOfferDraftLine line = new SupplierOfferDraftLine(draft, 1, item.title(), sourceReference);
            BigDecimal minimumQuantity = BigDecimal.valueOf(item.minimumQuantity());
            BigDecimal initialUnitPrice = item.unitPriceFor(minimumQuantity);
            String notes = "도매꾹 #" + item.itemId() + " · 판매자 " + item.sellerId()
                    + (item.businessOnly() ? " · 사업자 전용" : "")
                    + (item.priceTiers().isEmpty() ? "" : " · 구간가 " + formatTiers(item.priceTiers()))
                    + (item.shippingFee() == null ? "" : " · 배송비 " + item.shippingFee())
                    + (mode == DomeggookCatalogGateway.Mode.SAMPLE ? " · 샘플 데이터" : "");
            line.applyExtraction(item.title(), item.itemId(), "EA", minimumQuantity,
                    initialUnitPrice, null, notes, item.priceTiers().isEmpty() ? List.of()
                            : List.of("희망 수량에 따라 구간 단가가 자동 적용됩니다."));
            line.availableQuantity(item.availableQuantity());
            line.priceTiers(item.priceTiers());
            draft.addLine(line);
            return SupplierOfferResponse.from(drafts.saveAndFlush(draft));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("동일한 API 원본 또는 판매자 정보가 이미 등록되었습니다.");
        }
    }

    private static String formatTiers(List<WholesalePriceTier> tiers) {
        return tiers.stream().map(tier -> tier.minimumQuantity().stripTrailingZeros().toPlainString()
                + "+" + tier.unitPrice().stripTrailingZeros().toPlainString())
                .reduce((left, right) -> left + "|" + right).orElse("");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e); }
    }
}
