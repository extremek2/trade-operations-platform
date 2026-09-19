import { useCallback, useEffect, useMemo, useState } from "react";
import { getCostScenarios } from "../api/costingApi";
import { getShipments } from "../api/shipmentApi";
import {
  addActualCost, addReorderDecision, addSalesObservation, allocateShipment,
  approvePurchaseOrder, cancelPurchaseOrder, closeActualCost, createPurchaseOrder,
  createReceipt, getTradeCycles, recordPurchasePayment,
} from "../api/tradeCycleApi";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import StatusBadge from "../components/StatusBadge";
import { useAuth } from "../context/AuthContext";

const editableRoles = ["OWNER", "ADMIN", "OPERATOR"];
const statusLabels = {
  DRAFT: "발주 초안", APPROVED: "발주 승인", PARTIALLY_SHIPPED: "부분 선적", SHIPPED: "전량 선적",
  COMPLETED: "입고 완료", CANCELLED: "취소", UNPAID: "미결제", PARTIALLY_PAID: "부분 결제", PAID: "결제 완료",
  REORDER: "재발주", WATCH: "관찰", STOP: "중단",
};
const money = value => value == null ? "—" : `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 2 }).format(value)}원`;
const quantity = value => new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 6 }).format(value || 0);
const toNumber = value => Number(value);

const initial = {
  scenarioIds: [], orderNumber: "", notes: "", shipmentId: "", purchaseOrderLineId: "", allocationQuantity: "",
  allocationId: "", receiptNumber: "", receiptQuantity: "", lotNumber: "",
  paymentStatus: "PAID", paidAmount: "", paidAt: "", paymentEvidence: "",
  costType: "PRODUCT", costDescription: "", amountKrw: "", evidenceReference: "",
  inventoryLotId: "", channel: "", soldQuantity: "", returnedQuantity: "0",
  grossRevenueKrw: "", channelCostKrw: "0", decisionProductId: "", decision: "WATCH", reason: "",
};

export default function TradeCyclePage({ navigate }) {
  const { user } = useAuth();
  const editable = editableRoles.includes(user?.role);
  const [scenarios, setScenarios] = useState([]);
  const [shipments, setShipments] = useState([]);
  const [cycles, setCycles] = useState([]);
  const [activeId, setActiveId] = useState("");
  const [form, setForm] = useState(initial);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [scenarioRows, shipmentRows, cycleRows] = await Promise.all([
        getCostScenarios(), getShipments(), getTradeCycles(),
      ]);
      const supersededScenarioIds = new Set(scenarioRows.map(item => item.previousScenarioId).filter(Boolean));
      const orderedScenarioIds = new Set(cycleRows.flatMap(cycle => cycle.purchaseOrder.lines.map(line => line.costScenarioId)));
      setScenarios(scenarioRows.filter(item => item.calculationStatus !== "INCOMPLETE" && item.quoteStatus === "SELECTED"
        && !supersededScenarioIds.has(item.scenarioId) && !orderedScenarioIds.has(item.scenarioId)));
      setShipments(shipmentRows.filter(item => ["OPEN", "ON_HOLD"].includes(item.status)));
      setCycles(cycleRows);
      setActiveId(current => current || cycleRows[0]?.purchaseOrder.purchaseOrderId || "");
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const active = cycles.find(item => item.purchaseOrder.purchaseOrderId === activeId) || cycles[0];
  const availableAllocations = useMemo(() => (active?.shipmentAllocations || []).filter(item =>
    Number(item.allocatedQuantity) > Number(item.receivedQuantity)), [active]);
  const availableLots = useMemo(() => (active?.inventoryLots || []).filter(item => Number(item.sellableQuantity) > 0), [active]);
  const allocatableLines = useMemo(() => (active?.purchaseOrder.lines || []).filter(line =>
    Number(line.shippedQuantity) < Number(line.orderedQuantity)), [active]);
  const allocationLine = allocatableLines.find(line => line.purchaseOrderLineId === form.purchaseOrderLineId);
  const ordered = active?.purchaseOrder.lines.reduce((sum, line) => sum + Number(line.orderedQuantity), 0) || 0;
  const shipped = active?.purchaseOrder.lines.reduce((sum, line) => sum + Number(line.shippedQuantity), 0) || 0;
  const received = active?.purchaseOrder.lines.reduce((sum, line) => sum + Number(line.receivedQuantity), 0) || 0;

  const update = (field, value) => setForm(current => ({ ...current, [field]: value }));
  const replace = cycle => {
    const id = cycle.purchaseOrder.purchaseOrderId;
    setCycles(current => [cycle, ...current.filter(item => item.purchaseOrder.purchaseOrderId !== id)]);
    setActiveId(id);
  };
  const perform = async (action, resetFields = {}) => {
    setSaving(true); setError("");
    try { replace(await action()); setForm(current => ({ ...current, ...resetFields })); }
    catch (err) { setError(err.message); }
    finally { setSaving(false); }
  };
  const submit = action => event => { event.preventDefault(); action(); };

  const toggleScenario = scenario => setForm(current => ({
    ...current,
    scenarioIds: current.scenarioIds.includes(scenario.scenarioId)
      ? current.scenarioIds.filter(id => id !== scenario.scenarioId)
      : [...current.scenarioIds, scenario.scenarioId],
  }));

  const createOrder = () => perform(() => createPurchaseOrder({
    costScenarioIds: form.scenarioIds, orderNumber: form.orderNumber, ...(form.notes && { notes: form.notes }),
  }), { scenarioIds: [], orderNumber: "", notes: "" });
  const allocate = () => perform(() => allocateShipment({
    shipmentId: form.shipmentId,
    purchaseOrderLineId: form.purchaseOrderLineId,
    quantity: toNumber(form.allocationQuantity),
  }), { shipmentId: "", purchaseOrderLineId: "", allocationQuantity: "" });
  const receive = () => perform(() => createReceipt({
    shipmentId: active.shipmentAllocations.find(item => item.allocationId === form.allocationId).shipmentId,
    receiptNumber: form.receiptNumber,
    lines: [{ allocationId: form.allocationId, quantity: toNumber(form.receiptQuantity), lotNumber: form.lotNumber }],
  }), { allocationId: "", receiptNumber: "", receiptQuantity: "", lotNumber: "" });
  const addCost = () => perform(() => addActualCost(active.purchaseOrder.purchaseOrderId, {
    costType: form.costType, description: form.costDescription, amountKrw: toNumber(form.amountKrw),
    ...(form.evidenceReference && { evidenceReference: form.evidenceReference }),
  }), { costDescription: "", amountKrw: "", evidenceReference: "" });
  const addSale = () => perform(() => addSalesObservation(active.purchaseOrder.purchaseOrderId, {
    inventoryLotId: form.inventoryLotId, channel: form.channel,
    soldQuantity: toNumber(form.soldQuantity), returnedQuantity: toNumber(form.returnedQuantity),
    grossRevenueKrw: toNumber(form.grossRevenueKrw), channelCostKrw: toNumber(form.channelCostKrw),
  }), { inventoryLotId: "", channel: "", soldQuantity: "", returnedQuantity: "0", grossRevenueKrw: "", channelCostKrw: "0" });

  return <>
    <header className="page-header"><div><span className="eyebrow">END-TO-END IMPORT CYCLE</span><h1>수입 운영 사이클</h1>
      <p>선택 견적의 발주부터 선적·입고·실제 원가·판매 결과·재발주 판단까지 한 줄로 추적합니다.</p></div></header>
    <ErrorMessage message={error}/>
    {loading ? <LoadingState label="수입 사이클을 불러오는 중입니다."/> : <>
      {editable && <section className="panel cycle-create">
        <div className="panel-header"><div><h2>예상 원가에서 발주 만들기</h2><p className="muted">공급 견적 화면에서 SELECTED로 확정한 완성 원가안만 발주할 수 있습니다.</p></div></div>
        {scenarios.length === 0 ? <EmptyState title="사용할 원가안이 없습니다." description="예상 원가 계산을 완료한 뒤 돌아오세요."/> :
          <form className="panel-body form-grid three" aria-label="발주 생성" onSubmit={submit(createOrder)}>
            <fieldset className="quote-line-editor"><legend>예상 원가안</legend>{scenarios.map(item => {
              const selectedQuoteId = scenarios.find(row => form.scenarioIds.includes(row.scenarioId))?.quoteId;
              const disabled = Boolean(selectedQuoteId && selectedQuoteId !== item.quoteId);
              return <label key={item.scenarioId}><input type="checkbox" checked={form.scenarioIds.includes(item.scenarioId)}
                disabled={disabled} onChange={() => toggleScenario(item)}/>{item.productName} · {item.scenarioName} R{item.revisionNumber} · {quantity(item.orderQuantity)}개</label>;
            })}</fieldset>
            <label>발주번호<input required maxLength="100" value={form.orderNumber} onChange={e => update("orderNumber", e.target.value)}/></label>
            <label>메모<input maxLength="2000" value={form.notes} onChange={e => update("notes", e.target.value)}/></label>
            <div className="form-actions cycle-submit"><button disabled={saving || form.scenarioIds.length === 0} className="button primary">발주 초안 생성</button></div>
          </form>}
      </section>}

      {cycles.length === 0 ? <section className="panel"><EmptyState title="아직 발주 사이클이 없습니다." description="선택한 예상 원가안으로 첫 발주를 만드세요."/></section> : <>
        <section className="cycle-picker"><label>작업할 발주<select value={active?.purchaseOrder.purchaseOrderId || ""} onChange={e => setActiveId(e.target.value)}>
          {cycles.map(item => <option key={item.purchaseOrder.purchaseOrderId} value={item.purchaseOrder.purchaseOrderId}>{item.purchaseOrder.orderNumber} · {item.purchaseOrder.lines[0]?.productName}</option>)}</select></label></section>
        <CycleSummary cycle={active} ordered={ordered} shipped={shipped} received={received}/>

        {editable && active.purchaseOrder.status === "DRAFT" && <section className="panel cycle-action"><div><h2>1. 발주 확정</h2><p className="muted">발주 조건을 확인한 뒤 승인하면 선적 배정이 열립니다.</p></div><div className="cycle-buttons">
          <button disabled={saving} className="button primary" onClick={() => perform(() => approvePurchaseOrder(active.purchaseOrder.purchaseOrderId, active.purchaseOrder.version))}>발주 승인</button>
          {user?.role !== "OPERATOR" && <button disabled={saving} className="button danger" onClick={() => perform(() => cancelPurchaseOrder(active.purchaseOrder.purchaseOrderId, active.purchaseOrder.version))}>발주 취소</button>}
        </div></section>}

        {editable && !["DRAFT", "CANCELLED", "COMPLETED"].includes(active.purchaseOrder.status) && <section className="cycle-grid">
          <form className="panel panel-body form-stack" aria-label="결제 기록" onSubmit={submit(() => perform(() => recordPurchasePayment(active.purchaseOrder.purchaseOrderId, {
            version: active.purchaseOrder.version, paymentStatus: form.paymentStatus, paidAmount: toNumber(form.paidAmount), paidAt: form.paidAt,
            ...(form.paymentEvidence && { evidenceReference: form.paymentEvidence }),
          }), { paidAmount: "", paidAt: "", paymentEvidence: "" }))}>
            <h2>결제 상태 기록</h2><label>상태<select value={form.paymentStatus} onChange={e => update("paymentStatus", e.target.value)}><option value="PARTIALLY_PAID">부분 결제</option><option value="PAID">결제 완료</option><option value="UNPAID">미결제</option></select></label>
            <label>누적 결제금액<input required type="number" min="0" step="0.0001" value={form.paidAmount} onChange={e => update("paidAmount", e.target.value)}/></label>
            <label>결제일<input required={form.paymentStatus !== "UNPAID"} type="date" value={form.paidAt} onChange={e => update("paidAt", e.target.value)}/></label>
            <label>증빙 참조<input value={form.paymentEvidence} onChange={e => update("paymentEvidence", e.target.value)}/></label><button disabled={saving} className="button secondary">기록</button>
          </form>
          {allocatableLines.length > 0 && <form className="panel panel-body form-stack" aria-label="선적 배정" onSubmit={submit(allocate)}>
            <h2>2. 선적 배정</h2>{shipments.length === 0 ? <><p className="muted">배정할 진행 화물이 없습니다.</p><button type="button" className="button ghost" onClick={() => navigate("/shipments/new")}>화물 먼저 등록</button></> : <>
              <label>화물<select required value={form.shipmentId} onChange={e => update("shipmentId", e.target.value)}><option value="">선택</option>{shipments.map(item => <option value={item.shipmentId} key={item.shipmentId}>{item.caseNumber}</option>)}</select></label>
              <label>발주 품목<select required value={form.purchaseOrderLineId} onChange={e => update("purchaseOrderLineId", e.target.value)}><option value="">선택</option>{allocatableLines.map(line => <option value={line.purchaseOrderLineId} key={line.purchaseOrderLineId}>{line.productName} · 잔여 {quantity(Number(line.orderedQuantity) - Number(line.shippedQuantity))} {line.quantityUnit}</option>)}</select></label>
              <label>배정수량<input required type="number" min="0.000001" max={allocationLine ? Number(allocationLine.orderedQuantity) - Number(allocationLine.shippedQuantity) : undefined} step="any" value={form.allocationQuantity} onChange={e => update("allocationQuantity", e.target.value)}/></label>
              <button disabled={saving} className="button secondary">선적에 배정</button></>}
          </form>}
        </section>}

        {editable && availableAllocations.length > 0 && <form className="panel panel-body cycle-receipt form-grid four" aria-label="입고 처리" onSubmit={submit(receive)}>
          <div className="cycle-form-heading"><h2>3. 입고와 재고 로트</h2><p className="muted">부분 입고도 가능하며 입력 건마다 판매 가능한 재고 로트가 생깁니다.</p></div>
          <label>선적 배정<select required value={form.allocationId} onChange={e => update("allocationId", e.target.value)}><option value="">선택</option>{availableAllocations.map(item => <option value={item.allocationId} key={item.allocationId}>{active.purchaseOrder.lines.find(line => line.purchaseOrderLineId === item.purchaseOrderLineId)?.productName} · {item.shipmentCaseNumber} · 잔여 {quantity(Number(item.allocatedQuantity) - Number(item.receivedQuantity))}</option>)}</select></label>
          <label>입고번호<input required value={form.receiptNumber} onChange={e => update("receiptNumber", e.target.value)}/></label>
          <label>입고수량<input required type="number" min="0.000001" step="any" value={form.receiptQuantity} onChange={e => update("receiptQuantity", e.target.value)}/></label>
          <label>로트번호<input required value={form.lotNumber} onChange={e => update("lotNumber", e.target.value)}/></label>
          <div className="form-actions cycle-submit"><button disabled={saving} className="button secondary">입고 확정</button></div>
        </form>}

        {editable && active.purchaseOrder.status === "COMPLETED" && !active.costClose && <section className="cycle-grid">
          <form className="panel panel-body form-stack" aria-label="실제 비용 기록" onSubmit={submit(addCost)}><h2>4. 실제 비용 입력</h2>
            <label>비용 유형<select value={form.costType} onChange={e => update("costType", e.target.value)}>{["PRODUCT","FREIGHT","DUTY","TAX","CLEARANCE","INSPECTION","WAREHOUSE","DOMESTIC_DELIVERY","OTHER"].map(type => <option key={type}>{type}</option>)}</select></label>
            <label>설명<input required value={form.costDescription} onChange={e => update("costDescription", e.target.value)}/></label>
            <label>금액 (원)<input required type="number" min="0" step="0.01" value={form.amountKrw} onChange={e => update("amountKrw", e.target.value)}/></label>
            <label>증빙 참조<input value={form.evidenceReference} onChange={e => update("evidenceReference", e.target.value)}/></label><button disabled={saving} className="button secondary">비용 추가</button>
          </form>
          <div className="panel panel-body form-stack"><h2>수량 기준 원가 마감</h2><p className="muted">입고 로트의 수량 비율로 실제 비용을 배부합니다. 원 단위 잔여액은 마지막 로트에 반영됩니다.</p>
            <strong>입력 합계 {money(active.actualCosts.reduce((sum, item) => sum + Number(item.amountKrw), 0))}</strong>
            <button disabled={saving || active.actualCosts.length === 0} className="button primary" onClick={() => perform(() => closeActualCost(active.purchaseOrder.purchaseOrderId))}>실제 원가 마감</button></div>
        </section>}

        {editable && active.costClose && <section className="cycle-grid">
          <form className="panel panel-body form-stack" aria-label="판매 결과 기록" onSubmit={submit(addSale)}><h2>5. 판매 결과</h2>
            <label>재고 로트<select required value={form.inventoryLotId} onChange={e => update("inventoryLotId", e.target.value)}><option value="">선택</option>{availableLots.map(lot => <option value={lot.inventoryLotId} key={lot.inventoryLotId}>{lot.lotNumber} · 판매가능 {quantity(lot.sellableQuantity)}</option>)}</select></label>
            <label>채널<input required value={form.channel} onChange={e => update("channel", e.target.value)}/></label>
            <div className="form-grid two"><label>판매수량<input required type="number" min="0" step="any" value={form.soldQuantity} onChange={e => update("soldQuantity", e.target.value)}/></label><label>반품수량<input required type="number" min="0" step="any" value={form.returnedQuantity} onChange={e => update("returnedQuantity", e.target.value)}/></label></div>
            <div className="form-grid two"><label>총매출<input required type="number" min="0" step="0.01" value={form.grossRevenueKrw} onChange={e => update("grossRevenueKrw", e.target.value)}/></label><label>채널비용<input required type="number" min="0" step="0.01" value={form.channelCostKrw} onChange={e => update("channelCostKrw", e.target.value)}/></label></div>
            <button disabled={saving || availableLots.length === 0} className="button secondary">판매 관측 추가</button>
          </form>
          <form className="panel panel-body form-stack" aria-label="재발주 판단" onSubmit={submit(() => perform(() => addReorderDecision(active.purchaseOrder.purchaseOrderId, {
            productId: form.decisionProductId, decision: form.decision, reason: form.reason,
          }), { reason: "" }))}><h2>6. 재발주 판단</h2>
            <label>판단 상품<select required value={form.decisionProductId} onChange={e => update("decisionProductId", e.target.value)}><option value="">선택</option>{active.purchaseOrder.lines.map(line => <option value={line.productId} key={line.productId}>{line.productName}</option>)}</select></label>
            <label>판단<select value={form.decision} onChange={e => update("decision", e.target.value)}><option value="REORDER">재발주</option><option value="WATCH">관찰</option><option value="STOP">중단</option></select></label>
            <label>근거<textarea required value={form.reason} onChange={e => update("reason", e.target.value)}/></label>
            <button disabled={saving || active.salesObservations.length === 0} className="button primary">판단 기록</button>
          </form>
        </section>}
      </>}
    </>}
  </>;
}

function CycleSummary({ cycle, ordered, shipped, received }) {
  const order = cycle.purchaseOrder;
  const title = order.lines.length > 1 ? `${order.lines[0]?.productName} 외 ${order.lines.length - 1}품목` : order.lines[0]?.productName;
  return <section className="panel cycle-summary">
    <div className="panel-header"><div><span className="eyebrow">{order.orderNumber}</span><div className="title-row"><h2>{title}</h2><StatusBadge value={order.status}/></div><p className="muted">{order.supplierName} · {order.lines.length}개 발주 품목 · 예상 {money(order.expectedTotalCostKrw)}</p></div><div><StatusBadge value={order.paymentStatus}/><small className="cycle-status-label">{statusLabels[order.status]}</small></div></div>
    <ul>{order.lines.map(line => <li key={line.purchaseOrderLineId}>{line.productName} · {quantity(line.orderedQuantity)} {line.quantityUnit} · {order.currency} {quantity(line.unitPrice)}</li>)}</ul>
    <div className="cycle-progress"><div><span>발주</span><strong>{quantity(ordered)}</strong></div><div><span>선적</span><strong>{quantity(shipped)}</strong></div><div><span>입고</span><strong>{quantity(received)}</strong></div><div><span>판매가능</span><strong>{quantity(cycle.inventoryLots.reduce((sum, lot) => sum + Number(lot.sellableQuantity), 0))}</strong></div></div>
    <div className="cycle-ledger">
      <section><h3>실제 원가</h3>{cycle.costClose ? <><strong>{money(cycle.costClose.actualTotalKrw)}</strong><small>예상 대비 {money(cycle.costClose.varianceKrw)} · 단위 {money(cycle.costClose.actualUnitCostKrw)}</small></> : <span>미마감 · 비용 {cycle.actualCosts.length}건</span>}</section>
      <section><h3>판매 관측</h3><strong>{cycle.salesObservations.length}건</strong><small>총매출 {money(cycle.salesObservations.reduce((sum, item) => sum + Number(item.grossRevenueKrw), 0))}</small></section>
      <section><h3>최근 판단</h3>{cycle.decisions[0] ? <><StatusBadge value={cycle.decisions[0].decision}/><small>{cycle.decisions[0].productName} · {cycle.decisions[0].reason}</small></> : <span>아직 판단 없음</span>}</section>
    </div>
  </section>;
}
