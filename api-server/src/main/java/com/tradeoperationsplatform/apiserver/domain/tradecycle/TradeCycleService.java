package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.costing.entity.CostScenario;
import com.tradeoperationsplatform.apiserver.domain.costing.repository.CostScenarioRepository;
import com.tradeoperationsplatform.apiserver.domain.shipment.entity.ShipmentCase;
import com.tradeoperationsplatform.apiserver.domain.shipment.repository.ShipmentCaseRepository;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuoteLine;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import com.tradeoperationsplatform.apiserver.global.exception.ConflictException;
import com.tradeoperationsplatform.apiserver.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TradeCycleService {
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository orderLines;
    private final CostScenarioRepository scenarios;
    private final ShipmentCaseRepository shipments;
    private final ShipmentAllocationRepository allocations;
    private final ReceiptRepository receipts;
    private final ReceiptLineRepository receiptLines;
    private final InventoryLotRepository lots;
    private final ActualCostItemRepository actualCosts;
    private final ActualCostCloseRepository costCloses;
    private final SalesObservationRepository sales;
    private final ReorderDecisionRepository decisions;
    private final CurrentActor currentActor;

    @Transactional
    public TradeCycleResponse createOrder(TradeCycleRequests.CreatePurchaseOrder request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        if (new HashSet<>(request.costScenarioIds()).size() != request.costScenarioIds().size()) {
            throw new BusinessException("같은 원가 시나리오를 발주에 중복 입력할 수 없습니다.");
        }
        List<CostScenario> selectedScenarios = request.costScenarioIds().stream().map(scenarioId ->
                scenarios.findByPublicIdAndOrganizationId(scenarioId, organizationId)
                        .orElseThrow(() -> new ResourceNotFoundException("예상 원가 시나리오를 찾을 수 없습니다."))).toList();
        selectedScenarios.forEach(scenario -> {
            if (scenario.getQuote().getStatus() != SupplierQuote.Status.SELECTED) {
                throw new BusinessException("선택 상태의 견적만 발주로 전환할 수 있습니다.");
            }
            if (orderLines.existsByCostScenarioId(scenario.getId())) {
                throw new ConflictException("이미 발주에 연결된 원가 시나리오입니다.");
            }
        });
        if (orders.existsByOrganizationIdAndOrderNumber(organizationId, request.orderNumber().trim())) {
            throw new ConflictException("조직 내에서 이미 사용 중인 발주번호입니다.");
        }
        try {
            PurchaseOrder order = new PurchaseOrder(actor.organization(), actor.user(), selectedScenarios,
                    request.orderNumber(), request.orderedAt(), request.notes());
            int lineNumber = 1;
            for (CostScenario scenario : selectedScenarios) {
                SupplierQuoteLine quoteLine = scenario.getQuote().getLines().stream()
                        .filter(line -> line.getProduct().getId().equals(scenario.getProduct().getId()))
                        .findFirst().orElseThrow(() -> new BusinessException("원가 시나리오의 견적 품목을 찾을 수 없습니다."));
                order.addLine(new PurchaseOrderLine(order, scenario, lineNumber++, quoteLine.getQuantityUnit()));
            }
            return response(orders.saveAndFlush(order));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("발주번호 또는 발주 연결정보가 이미 사용 중입니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<TradeCycleResponse> findAll() {
        Long organizationId = currentActor.require().organization().getId();
        return orders.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public TradeCycleResponse findOne(UUID orderId) { return response(requireOrder(orderId)); }

    @Transactional
    public TradeCycleResponse approve(UUID orderId, TradeCycleRequests.OrderAction request) {
        PurchaseOrder order = requireOrder(orderId);
        checkVersion(order, request.version());
        try { order.approve(); } catch (IllegalStateException e) { throw new BusinessException(e.getMessage()); }
        orders.saveAndFlush(order);
        return response(order);
    }

    @Transactional
    public TradeCycleResponse cancel(UUID orderId, TradeCycleRequests.OrderAction request) {
        PurchaseOrder order = requireOrder(orderId);
        checkVersion(order, request.version());
        try { order.cancel(); } catch (IllegalStateException e) { throw new BusinessException(e.getMessage()); }
        orders.saveAndFlush(order);
        return response(order);
    }

    @Transactional
    public TradeCycleResponse recordPayment(UUID orderId, TradeCycleRequests.Payment request) {
        PurchaseOrder order = requireOrder(orderId);
        checkVersion(order, request.version());
        try {
            order.recordPayment(request.paymentStatus(), request.paidAmount(), request.paidAt(), request.evidenceReference());
        } catch (IllegalArgumentException | IllegalStateException e) { throw new BusinessException(e.getMessage()); }
        orders.saveAndFlush(order);
        return response(order);
    }

    @Transactional
    public TradeCycleResponse allocate(TradeCycleRequests.AllocateShipment request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        ShipmentCase shipment = shipments.findByPublicIdAndOwnerOrganizationPublicId(
                        request.shipmentId(), actor.organization().getPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("해당 조직의 화물을 찾을 수 없습니다."));
        PurchaseOrderLine line = orderLines.findByPublicIdAndPurchaseOrderOrganizationId(
                        request.purchaseOrderLineId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("발주 품목을 찾을 수 없습니다."));
        PurchaseOrder order = line.getPurchaseOrder();
        if (!EnumSet.of(PurchaseOrder.Status.APPROVED, PurchaseOrder.Status.PARTIALLY_SHIPPED,
                PurchaseOrder.Status.SHIPPED).contains(order.getStatus())) {
            throw new BusinessException("승인되어 진행 중인 발주만 선적에 배정할 수 있습니다.");
        }
        if (shipment.getArchivedAt() != null || EnumSet.of(ShipmentCase.Status.CANCELLED,
                ShipmentCase.Status.COMPLETED).contains(shipment.getStatus())) {
            throw new BusinessException("진행 중인 화물에만 발주 품목을 배정할 수 있습니다.");
        }
        if (allocations.existsByShipmentIdAndPurchaseOrderLineId(shipment.getId(), line.getId())) {
            throw new ConflictException("이 화물에 이미 배정된 발주 품목입니다.");
        }
        BigDecimal next = allocations.sumAllocated(line.getId()).add(request.quantity());
        if (next.compareTo(line.getOrderedQuantity()) > 0) throw new BusinessException("선적 배정량이 발주수량을 초과합니다.");
        try {
            allocations.saveAndFlush(new ShipmentAllocation(actor.organization(), actor.user(), shipment, line, request.quantity()));
            refreshProgress(order);
            orders.saveAndFlush(order);
            return response(order);
        } catch (DataIntegrityViolationException e) { throw new ConflictException("선적 배정 정보가 변경되었습니다."); }
    }

    @Transactional
    public TradeCycleResponse receive(TradeCycleRequests.CreateReceipt request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        ShipmentCase shipment = shipments.findByPublicIdAndOwnerOrganizationPublicId(
                        request.shipmentId(), actor.organization().getPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("해당 조직의 화물을 찾을 수 없습니다."));
        if (shipment.getArchivedAt() != null || shipment.getStatus() == ShipmentCase.Status.CANCELLED) {
            throw new BusinessException("취소되거나 보관된 화물은 입고할 수 없습니다.");
        }
        if (receipts.existsByOrganizationIdAndReceiptNumber(organizationId, request.receiptNumber().trim())) {
            throw new ConflictException("조직 내에서 이미 사용 중인 입고번호입니다.");
        }
        Set<UUID> allocationIds = new HashSet<>();
        Set<String> lotNumbers = new HashSet<>();
        List<ResolvedReceipt> resolved = new ArrayList<>();
        for (TradeCycleRequests.ReceiptEntry entry : request.lines()) {
            if (!allocationIds.add(entry.allocationId())) throw new BusinessException("한 입고에 같은 선적 배정을 중복 입력할 수 없습니다.");
            String lotNumber = entry.lotNumber().trim();
            if (!lotNumbers.add(lotNumber) || lots.existsByOrganizationIdAndLotNumber(organizationId, lotNumber)) {
                throw new ConflictException("조직 내에서 이미 사용 중인 로트번호입니다: " + lotNumber);
            }
            ShipmentAllocation allocation = allocations.findByPublicIdAndOrganizationId(entry.allocationId(), organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("선적 배정을 찾을 수 없습니다."));
            if (!allocation.getShipment().getId().equals(shipment.getId())) throw new BusinessException("선택한 화물의 배정만 입고할 수 있습니다.");
            BigDecimal received = receiptLines.sumReceivedByAllocation(allocation.getId());
            if (received.add(entry.quantity()).compareTo(allocation.getAllocatedQuantity()) > 0) {
                throw new BusinessException("입고수량이 해당 선적 배정량을 초과합니다.");
            }
            resolved.add(new ResolvedReceipt(allocation, entry));
        }
        try {
            Receipt receipt = new Receipt(actor.organization(), actor.user(), shipment, request.receiptNumber(),
                    request.receivedAt(), request.notes());
            Set<PurchaseOrder> touched = new HashSet<>();
            for (ResolvedReceipt item : resolved) {
                PurchaseOrderLine line = item.allocation().getPurchaseOrderLine();
                InventoryLot lot = new InventoryLot(actor.organization(), line, receipt,
                        item.entry().lotNumber(), item.entry().quantity());
                receipt.addLine(new ReceiptLine(receipt, item.allocation(), lot, item.entry().quantity()));
                touched.add(line.getPurchaseOrder());
            }
            receipts.saveAndFlush(receipt);
            touched.forEach(order -> { refreshProgress(order); orders.saveAndFlush(order); });
            return response(touched.iterator().next());
        } catch (IllegalArgumentException e) { throw new BusinessException(e.getMessage()); }
        catch (DataIntegrityViolationException e) { throw new ConflictException("입고번호나 로트번호가 이미 사용 중입니다."); }
    }

    @Transactional
    public TradeCycleResponse addActualCost(UUID orderId, TradeCycleRequests.AddActualCost request) {
        var actor = currentActor.require();
        PurchaseOrder order = requireOrder(orderId);
        if (costCloses.existsByPurchaseOrderId(order.getId())) throw new BusinessException("원가 마감 후에는 실제 비용을 추가할 수 없습니다.");
        try {
            actualCosts.save(new ActualCostItem(actor.organization(), actor.user(), order, request.costType(),
                    request.description(), request.amountKrw(), request.incurredAt(), request.evidenceReference()));
            return response(order);
        } catch (IllegalArgumentException e) { throw new BusinessException(e.getMessage()); }
    }

    @Transactional
    public TradeCycleResponse closeCost(UUID orderId) {
        var actor = currentActor.require();
        PurchaseOrder order = requireOrder(orderId);
        if (order.getStatus() != PurchaseOrder.Status.COMPLETED) throw new BusinessException("전량 입고가 완료된 발주만 실제 원가를 마감할 수 있습니다.");
        if (costCloses.existsByPurchaseOrderId(order.getId())) throw new ConflictException("이미 실제 원가를 마감한 발주입니다.");
        List<ActualCostItem> items = actualCosts.findAllByPurchaseOrderIdOrderByCreatedAtAsc(order.getId());
        if (items.isEmpty()) throw new BusinessException("실제 비용을 한 건 이상 입력해 주세요.");
        List<InventoryLot> orderLots = lots.findAllByPurchaseOrderLinePurchaseOrderIdOrderByCreatedAtAsc(order.getId());
        BigDecimal received = orderLots.stream().map(InventoryLot::getReceivedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = items.stream().map(ActualCostItem::getAmountKrw).reduce(BigDecimal.ZERO, BigDecimal::add);
        ActualCostClose close = new ActualCostClose(actor.organization(), actor.user(), order, total, received);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < orderLots.size(); index++) {
            InventoryLot lot = orderLots.get(index);
            BigDecimal amount = index == orderLots.size() - 1
                    ? total.subtract(allocated).setScale(2, RoundingMode.HALF_UP)
                    : total.multiply(lot.getReceivedQuantity()).divide(received, 2, RoundingMode.DOWN);
            close.addAllocation(new ActualCostAllocation(close, lot, amount));
            allocated = allocated.add(amount);
        }
        try { costCloses.saveAndFlush(close); }
        catch (DataIntegrityViolationException e) { throw new ConflictException("실제 원가 마감 정보가 변경되었습니다."); }
        return response(order);
    }

    @Transactional
    public TradeCycleResponse addSales(UUID orderId, TradeCycleRequests.AddSalesObservation request) {
        var actor = currentActor.require();
        PurchaseOrder order = requireOrder(orderId);
        if (!costCloses.existsByPurchaseOrderId(order.getId())) throw new BusinessException("실제 원가를 마감한 뒤 판매 결과를 기록할 수 있습니다.");
        InventoryLot lot = lots.findByPublicIdAndOrganizationId(request.inventoryLotId(), actor.organization().getId())
                .orElseThrow(() -> new ResourceNotFoundException("재고 로트를 찾을 수 없습니다."));
        if (!lot.getPurchaseOrderLine().getPurchaseOrder().getId().equals(order.getId())) throw new BusinessException("이 발주의 재고 로트가 아닙니다.");
        if (request.soldQuantity().signum() == 0 && request.returnedQuantity().signum() == 0) {
            throw new BusinessException("판매 또는 반품 수량을 입력해 주세요.");
        }
        try {
            lot.observeSale(request.soldQuantity(), request.returnedQuantity());
            sales.save(new SalesObservation(actor.organization(), actor.user(), lot, request.channel(), request.observedAt(),
                    request.soldQuantity(), request.returnedQuantity(), request.grossRevenueKrw(),
                    request.channelCostKrw(), request.notes()));
            return response(order);
        } catch (IllegalArgumentException | IllegalStateException e) { throw new BusinessException(e.getMessage()); }
    }

    @Transactional
    public TradeCycleResponse addDecision(UUID orderId, TradeCycleRequests.AddDecision request) {
        var actor = currentActor.require();
        PurchaseOrder order = requireOrder(orderId);
        if (!costCloses.existsByPurchaseOrderId(order.getId())) throw new BusinessException("실제 원가 마감 후 판단을 기록할 수 있습니다.");
        if (sales.findAllByInventoryLotPurchaseOrderLinePurchaseOrderIdOrderByObservedAtAsc(order.getId()).isEmpty()) {
            throw new BusinessException("판매 결과를 한 건 이상 기록한 뒤 판단해 주세요.");
        }
        var product = order.getLines().stream().map(PurchaseOrderLine::getProduct)
                .filter(item -> item.getPublicId().equals(request.productId())).findFirst()
                .orElseThrow(() -> new BusinessException("이 발주의 상품만 재발주 판단 대상으로 선택할 수 있습니다."));
        try {
            decisions.save(new ReorderDecision(actor.organization(), actor.user(), order,
                    product, request.decision(), request.reason()));
            return response(order);
        } catch (IllegalArgumentException e) { throw new BusinessException(e.getMessage()); }
    }

    private PurchaseOrder requireOrder(UUID orderId) {
        Long organizationId = currentActor.require().organization().getId();
        return orders.findByPublicIdAndOrganizationId(orderId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("발주를 찾을 수 없습니다."));
    }

    private void checkVersion(PurchaseOrder order, Long version) {
        if (version == null || order.getVersion() != version) throw new ConflictException("발주 정보가 변경되었습니다. 최신 정보를 확인해 주세요.");
    }

    private void refreshProgress(PurchaseOrder order) {
        BigDecimal ordered = order.getLines().stream().map(PurchaseOrderLine::getOrderedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipped = order.getLines().stream().map(line -> allocations.sumAllocated(line.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = receiptLines.sumReceivedByOrder(order.getId());
        order.updateProgress(shipped, received, ordered);
    }

    private TradeCycleResponse response(PurchaseOrder order) {
        List<ShipmentAllocation> orderAllocations = allocations.findAllByPurchaseOrderLinePurchaseOrderIdOrderByCreatedAtAsc(order.getId());
        Map<Long, BigDecimal> receivedByAllocation = new HashMap<>();
        orderAllocations.forEach(item -> receivedByAllocation.put(item.getId(), receiptLines.sumReceivedByAllocation(item.getId())));
        List<InventoryLot> orderLots = lots.findAllByPurchaseOrderLinePurchaseOrderIdOrderByCreatedAtAsc(order.getId());
        Map<Long, BigDecimal> shippedByLine = new HashMap<>();
        Map<Long, BigDecimal> receivedByLine = new HashMap<>();
        for (PurchaseOrderLine line : order.getLines()) {
            shippedByLine.put(line.getId(), allocations.sumAllocated(line.getId()));
            receivedByLine.put(line.getId(), orderLots.stream().filter(lot -> lot.getPurchaseOrderLine().getId().equals(line.getId()))
                    .map(InventoryLot::getReceivedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        var orderView = new TradeCycleResponse.PurchaseOrderView(order.getPublicId(), order.getOrderNumber(), order.getStatus(),
                order.getPaymentStatus(), order.getPaidAmount(), order.getPaidAt(), order.getPaymentEvidence(), order.getCurrency(),
                order.getSupplier().getPublicId(), order.getSupplierNameSnapshot(), order.getQuoteNumberSnapshot(),
                order.getQuoteRevisionSnapshot(), order.getExpectedTotalCostKrw(), order.getOrderedAt(),
                order.getNotes(), order.getVersion(), order.getCreatedAt(), order.getLines().stream().map(line ->
                new TradeCycleResponse.LineView(line.getPublicId(), line.getCostScenario().getPublicId(),
                        line.getProduct().getPublicId(), line.getProductNameSnapshot(), line.getInternalSkuSnapshot(),
                        line.getOrderedQuantity(), shippedByLine.get(line.getId()),
                        receivedByLine.get(line.getId()), line.getQuantityUnit(), line.getUnitPrice())).toList());
        List<TradeCycleResponse.AllocationView> allocationViews = orderAllocations.stream().map(item ->
                new TradeCycleResponse.AllocationView(item.getPublicId(), item.getShipment().getPublicId(),
                        item.getShipment().getCaseNumber(), item.getPurchaseOrderLine().getPublicId(), item.getAllocatedQuantity(),
                        receivedByAllocation.get(item.getId()), item.getCreatedAt())).toList();
        List<TradeCycleResponse.InventoryLotView> lotViews = orderLots.stream().map(lot ->
                new TradeCycleResponse.InventoryLotView(lot.getPublicId(), lot.getLotNumber(), lot.getProduct().getPublicId(),
                        lot.getProduct().getName(), lot.getPurchaseOrderLine().getPublicId(), lot.getReceipt().getPublicId(),
                        lot.getReceipt().getReceiptNumber(), lot.getReceivedQuantity(), lot.getSellableQuantity(),
                        lot.getVersion(), lot.getCreatedAt())).toList();
        List<TradeCycleResponse.ActualCostView> costViews = actualCosts.findAllByPurchaseOrderIdOrderByCreatedAtAsc(order.getId()).stream().map(cost ->
                new TradeCycleResponse.ActualCostView(cost.getPublicId(), cost.getCostType(), cost.getDescription(),
                        cost.getAmountKrw(), cost.getIncurredAt(), cost.getEvidenceReference())).toList();
        TradeCycleResponse.CostCloseView closeView = costCloses.findByPurchaseOrderId(order.getId()).map(close ->
                new TradeCycleResponse.CostCloseView(close.getPublicId(), close.getExpectedTotalKrw(), close.getActualTotalKrw(),
                        close.getVarianceKrw(), close.getReceivedQuantity(), close.getActualUnitCostKrw(), close.getClosedAt(),
                        close.getAllocations().stream().map(allocation -> new TradeCycleResponse.CostAllocationView(
                                allocation.getInventoryLot().getPublicId(), allocation.getInventoryLot().getLotNumber(),
                                allocation.getAllocatedAmountKrw())).toList())).orElse(null);
        List<TradeCycleResponse.SalesView> salesViews = sales.findAllByInventoryLotPurchaseOrderLinePurchaseOrderIdOrderByObservedAtAsc(order.getId()).stream().map(sale ->
                new TradeCycleResponse.SalesView(sale.getPublicId(), sale.getInventoryLot().getPublicId(), sale.getInventoryLot().getLotNumber(),
                        sale.getChannel(), sale.getObservedAt(), sale.getSoldQuantity(), sale.getReturnedQuantity(),
                        sale.getGrossRevenueKrw(), sale.getChannelCostKrw(), sale.getNotes())).toList();
        List<TradeCycleResponse.DecisionView> decisionViews = decisions.findAllByPurchaseOrderIdOrderByDecidedAtDesc(order.getId()).stream().map(decision ->
                new TradeCycleResponse.DecisionView(decision.getPublicId(), decision.getProduct().getPublicId(),
                        decision.getProduct().getName(), decision.getDecision(), decision.getReason(), decision.getDecidedAt())).toList();
        return new TradeCycleResponse(orderView, allocationViews, lotViews, costViews, closeView, salesViews, decisionViews);
    }

    private record ResolvedReceipt(ShipmentAllocation allocation, TradeCycleRequests.ReceiptEntry entry) {}
}
