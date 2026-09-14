package com.tradeoperationsplatform.apiserver.domain.costing.service;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.costing.dto.CostScenarioRequests;
import com.tradeoperationsplatform.apiserver.domain.costing.dto.CostScenarioResponse;
import com.tradeoperationsplatform.apiserver.domain.costing.entity.CostScenario;
import com.tradeoperationsplatform.apiserver.domain.costing.repository.CostScenarioRepository;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuoteLine;
import com.tradeoperationsplatform.apiserver.domain.sourcing.repository.SupplierQuoteRepository;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import com.tradeoperationsplatform.apiserver.global.exception.ConflictException;
import com.tradeoperationsplatform.apiserver.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CostScenarioService {
    private final CostScenarioRepository scenarios;
    private final SupplierQuoteRepository quotes;
    private final CurrentActor currentActor;

    @Transactional
    public CostScenarioResponse create(CostScenarioRequests.Create request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        SupplierQuote quote = quotes.findByPublicIdAndOrganizationId(request.quoteId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("견적을 찾을 수 없습니다."));
        if (!EnumSet.of(SupplierQuote.Status.RECEIVED, SupplierQuote.Status.SELECTED,
                SupplierQuote.Status.EXPIRED, SupplierQuote.Status.REJECTED).contains(quote.getStatus())) {
            throw new BusinessException("수신된 견적부터 원가 시나리오를 만들 수 있습니다.");
        }
        SupplierQuoteLine quoteLine = quote.getLines().stream()
                .filter(line -> line.getProduct().getPublicId().equals(request.productId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("견적 품목을 찾을 수 없습니다."));
        CostScenario previous = request.previousScenarioId() == null ? null
                : scenarios.findByPublicIdAndOrganizationId(request.previousScenarioId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("이전 원가 시나리오를 찾을 수 없습니다."));
        if (previous != null && scenarios.existsByPreviousScenarioId(previous.getId())) {
            throw new ConflictException("이미 다음 원가 시나리오 개정본이 존재합니다.");
        }

        CostScenario.Assumptions assumptions = new CostScenario.Assumptions(
                request.orderQuantity(), request.excludedQuantity(), request.exchangeRate(), request.exchangeRateSource(),
                request.taxableFreightKrw(), request.customsClearanceCostKrw(), request.inspectionCostKrw(),
                request.warehouseCostKrw(), request.domesticDeliveryCostKrw(), request.otherCostKrw(),
                request.logisticsCostSource(), request.customsDutyRate(), request.vatRate(), request.taxRateSource(),
                request.vatTreatment(), request.targetSellingPriceKrw(), request.sellingFeeRate(),
                request.variableCostPerUnitKrw(), request.salesAssumptionSource());
        try {
            CostScenario scenario = new CostScenario(actor.organization(), actor.user(), quote, quoteLine, previous,
                    request.scenarioName(), assumptions);
            return CostScenarioResponse.from(scenarios.saveAndFlush(scenario));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("원가 시나리오 개정 정보가 변경되었습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<CostScenarioResponse> findAll() {
        var actor = currentActor.require();
        return scenarios.findAllByOrganizationIdOrderByCreatedAtDesc(actor.organization().getId()).stream()
                .map(CostScenarioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CostScenarioResponse findOne(UUID scenarioId) {
        var actor = currentActor.require();
        return CostScenarioResponse.from(scenarios.findByPublicIdAndOrganizationId(
                        scenarioId, actor.organization().getId())
                .orElseThrow(() -> new ResourceNotFoundException("원가 시나리오를 찾을 수 없습니다.")));
    }
}
