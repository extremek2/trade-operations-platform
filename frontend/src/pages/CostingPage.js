import { useCallback, useEffect, useMemo, useState } from "react";
import { createCostScenario, getCostScenarios } from "../api/costingApi";
import { getQuotes } from "../api/quoteApi";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import StatusBadge from "../components/StatusBadge";
import { useAuth } from "../context/AuthContext";

const editableRoles = ["OWNER", "ADMIN", "OPERATOR"];
const sourceLabels = {
  USER_ASSUMPTION: "사용자 가정",
  MARKET_REFERENCE: "시장 참고값",
  SUPPLIER_QUOTE: "공급처 제시값",
  PROFESSIONAL_CONFIRMATION: "전문가 확인값",
};
const vatLabels = {
  RECOVERABLE_EXCLUDED: "회수 가능 가정 · 원가 제외",
  CASH_COST_INCLUDED: "현금 원가에 포함",
};
const initialForm = () => ({
  scenarioName: "", quoteLineKey: "", previousScenarioId: "",
  orderQuantity: "", excludedQuantity: "0",
  exchangeRate: "", exchangeRateSource: "USER_ASSUMPTION",
  taxableFreightKrw: "0", customsClearanceCostKrw: "0", inspectionCostKrw: "0",
  warehouseCostKrw: "0", domesticDeliveryCostKrw: "0", otherCostKrw: "0",
  logisticsCostSource: "USER_ASSUMPTION",
  customsDutyRate: "0", vatRate: "10", taxRateSource: "USER_ASSUMPTION",
  vatTreatment: "RECOVERABLE_EXCLUDED",
  targetSellingPriceKrw: "", sellingFeeRate: "0", variableCostPerUnitKrw: "0",
  salesAssumptionSource: "USER_ASSUMPTION",
});
const number = value => Number(value);
const optionalNumber = value => value === "" ? undefined : Number(value);
const valueOrEmpty = value => value == null ? "" : String(value);
const money = value => value == null ? "미확인" : `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 2 }).format(value)}원`;

function SourceSelect({ value, onChange }) {
  return <select value={value} onChange={onChange}>
    <option value="">미확인</option>
    {Object.entries(sourceLabels).map(([key, label]) => <option value={key} key={key}>{label}</option>)}
  </select>;
}

export default function CostingPage() {
  const { user } = useAuth();
  const editable = editableRoles.includes(user?.role);
  const [quotes, setQuotes] = useState([]);
  const [scenarios, setScenarios] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [quoteRows, scenarioRows] = await Promise.all([getQuotes(), getCostScenarios()]);
      setQuotes(quoteRows); setScenarios(scenarioRows);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const quoteLines = useMemo(() => quotes
    .filter(quote => quote.status !== "DRAFT")
    .flatMap(quote => quote.lines.map(line => ({
      key: `${quote.quoteId}:${line.productId}`,
      quote,
      line,
    }))), [quotes]);
  const selected = quoteLines.find(item => item.key === form.quoteLineKey);
  const scenariosWithNextRevision = useMemo(
    () => new Set(scenarios.map(item => item.previousScenarioId).filter(Boolean)), [scenarios]);

  const selectQuoteLine = key => {
    const item = quoteLines.find(option => option.key === key);
    setForm(current => ({
      ...current,
      quoteLineKey: key,
      previousScenarioId: "",
      scenarioName: item ? `${item.line.productName} 기준안` : current.scenarioName,
      orderQuantity: item?.line.minimumQuantity == null ? current.orderQuantity : String(item.line.minimumQuantity),
      exchangeRate: item?.quote.currency === "KRW" ? "1" : current.exchangeRate,
    }));
  };

  const startRevision = scenario => {
    const key = `${scenario.quoteId}:${scenario.productId}`;
    setForm({
      scenarioName: scenario.scenarioName,
      quoteLineKey: key,
      previousScenarioId: scenario.scenarioId,
      orderQuantity: valueOrEmpty(scenario.orderQuantity),
      excludedQuantity: valueOrEmpty(scenario.excludedQuantity),
      exchangeRate: valueOrEmpty(scenario.exchangeRate),
      exchangeRateSource: scenario.exchangeRateSource || "",
      taxableFreightKrw: valueOrEmpty(scenario.taxableFreightKrw),
      customsClearanceCostKrw: valueOrEmpty(scenario.customsClearanceCostKrw),
      inspectionCostKrw: valueOrEmpty(scenario.inspectionCostKrw),
      warehouseCostKrw: valueOrEmpty(scenario.warehouseCostKrw),
      domesticDeliveryCostKrw: valueOrEmpty(scenario.domesticDeliveryCostKrw),
      otherCostKrw: valueOrEmpty(scenario.otherCostKrw),
      logisticsCostSource: scenario.logisticsCostSource || "",
      customsDutyRate: valueOrEmpty(scenario.customsDutyRate),
      vatRate: valueOrEmpty(scenario.vatRate),
      taxRateSource: scenario.taxRateSource || "",
      vatTreatment: scenario.vatTreatment,
      targetSellingPriceKrw: valueOrEmpty(scenario.targetSellingPriceKrw),
      sellingFeeRate: valueOrEmpty(scenario.sellingFeeRate),
      variableCostPerUnitKrw: valueOrEmpty(scenario.variableCostPerUnitKrw),
      salesAssumptionSource: scenario.salesAssumptionSource || "",
    });
    window.scrollTo(0, 0);
  };

  const submit = async event => {
    event.preventDefault(); setSaving(true); setError("");
    const [quoteId, productId] = form.quoteLineKey.split(":");
    const hasLogisticsValues = ["taxableFreightKrw", "customsClearanceCostKrw", "inspectionCostKrw",
      "warehouseCostKrw", "domesticDeliveryCostKrw", "otherCostKrw"].some(field => form[field] !== "");
    const hasTaxValues = ["customsDutyRate", "vatRate"].some(field => form[field] !== "");
    const hasSalesValues = ["targetSellingPriceKrw", "sellingFeeRate", "variableCostPerUnitKrw"]
      .some(field => form[field] !== "");
    const payload = {
      quoteId, productId,
      ...(form.previousScenarioId && { previousScenarioId: form.previousScenarioId }),
      scenarioName: form.scenarioName,
      orderQuantity: number(form.orderQuantity),
      excludedQuantity: number(form.excludedQuantity),
      exchangeRate: optionalNumber(form.exchangeRate),
      ...(form.exchangeRate !== "" && { exchangeRateSource: form.exchangeRateSource }),
      taxableFreightKrw: optionalNumber(form.taxableFreightKrw),
      customsClearanceCostKrw: optionalNumber(form.customsClearanceCostKrw),
      inspectionCostKrw: optionalNumber(form.inspectionCostKrw),
      warehouseCostKrw: optionalNumber(form.warehouseCostKrw),
      domesticDeliveryCostKrw: optionalNumber(form.domesticDeliveryCostKrw),
      otherCostKrw: optionalNumber(form.otherCostKrw),
      ...(hasLogisticsValues && form.logisticsCostSource && { logisticsCostSource: form.logisticsCostSource }),
      customsDutyRate: optionalNumber(form.customsDutyRate),
      vatRate: optionalNumber(form.vatRate),
      ...(hasTaxValues && form.taxRateSource && { taxRateSource: form.taxRateSource }),
      vatTreatment: form.vatTreatment,
      targetSellingPriceKrw: optionalNumber(form.targetSellingPriceKrw),
      sellingFeeRate: optionalNumber(form.sellingFeeRate),
      variableCostPerUnitKrw: optionalNumber(form.variableCostPerUnitKrw),
      ...(hasSalesValues && form.salesAssumptionSource && { salesAssumptionSource: form.salesAssumptionSource }),
    };
    try {
      await createCostScenario(payload);
      setForm(initialForm()); await load();
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  };

  const update = (field, value) => setForm(current => ({ ...current, [field]: value }));

  return <>
    <header className="page-header"><div><span className="eyebrow">EXPECTED COST</span><h1>예상 원가</h1>
      <p>수신 견적의 입력값을 보존하면서 환율·수량·비용 가정을 비교합니다.</p></div></header>
    <ErrorMessage message={error}/>
    {editable && <section className="panel costing-form-panel">
      <div className="panel-header"><div><h2>{form.previousScenarioId ? "원가 시나리오 새 버전" : "원가 시나리오 등록"}</h2>
        <p className="muted">0은 비용 없음, 빈 값은 미확인입니다. 관세·부가세 값은 법률·세무 판단이 아닌 사용자 가정입니다.</p></div>
        {form.previousScenarioId && <button className="button ghost" onClick={() => setForm(initialForm())}>새 시나리오로 전환</button>}
      </div>
      {quoteLines.length === 0 ? <EmptyState title="수신된 견적이 없습니다." description="공급 견적을 수신 상태로 전환한 뒤 계산하세요."/> :
        <form className="setup-form" aria-label="원가 시나리오 입력" onSubmit={submit}>
          <div className="form-grid two">
            <label>견적 품목<select required value={form.quoteLineKey} onChange={event => selectQuoteLine(event.target.value)} disabled={Boolean(form.previousScenarioId)}>
              <option value="">선택</option>{quoteLines.map(item => <option key={item.key} value={item.key}>
                {item.quote.supplierName} · {item.quote.quoteNumber || "번호 미입력"} R{item.quote.revisionNumber} · {item.line.productName}
              </option>)}
            </select></label>
            <label>시나리오명<input required maxLength="200" value={form.scenarioName} onChange={event => update("scenarioName", event.target.value)}/></label>
          </div>
          {selected && <div className="cost-source-summary">
            <span>견적 단가</span><strong>{selected.line.unitPrice == null ? "미확인" : `${selected.quote.currency} ${selected.line.unitPrice}`}</strong>
            <small>{selected.quote.supplierName} · 공급처 제시값</small>
          </div>}
          <fieldset className="cost-fieldset"><legend>수량과 환율</legend>
            <div className="form-grid four">
              <label>주문수량<input required type="number" min="0.000001" step="any" value={form.orderQuantity} onChange={event => update("orderQuantity", event.target.value)}/></label>
              <label>판매 제외수량<input required type="number" min="0" step="any" value={form.excludedQuantity} onChange={event => update("excludedQuantity", event.target.value)}/></label>
              <label>원화 환율<input type="number" min="0.000001" step="any" value={form.exchangeRate} onChange={event => update("exchangeRate", event.target.value)}/></label>
              <label>환율 출처<SourceSelect value={form.exchangeRateSource} onChange={event => update("exchangeRateSource", event.target.value)}/></label>
            </div>
          </fieldset>
          <fieldset className="cost-fieldset"><legend>물류·부대비용 (원)</legend>
            <div className="form-grid four">
              <label>과세 운임·보험<input type="number" min="0" step="0.01" value={form.taxableFreightKrw} onChange={event => update("taxableFreightKrw", event.target.value)}/></label>
              <label>통관비<input type="number" min="0" step="0.01" value={form.customsClearanceCostKrw} onChange={event => update("customsClearanceCostKrw", event.target.value)}/></label>
              <label>검사비<input type="number" min="0" step="0.01" value={form.inspectionCostKrw} onChange={event => update("inspectionCostKrw", event.target.value)}/></label>
              <label>창고비<input type="number" min="0" step="0.01" value={form.warehouseCostKrw} onChange={event => update("warehouseCostKrw", event.target.value)}/></label>
              <label>국내배송비<input type="number" min="0" step="0.01" value={form.domesticDeliveryCostKrw} onChange={event => update("domesticDeliveryCostKrw", event.target.value)}/></label>
              <label>기타비용<input type="number" min="0" step="0.01" value={form.otherCostKrw} onChange={event => update("otherCostKrw", event.target.value)}/></label>
              <label>비용 출처<SourceSelect value={form.logisticsCostSource} onChange={event => update("logisticsCostSource", event.target.value)}/></label>
            </div>
          </fieldset>
          <fieldset className="cost-fieldset"><legend>세율과 부가세 관점</legend>
            <div className="form-grid four">
              <label>관세율 (%)<input type="number" min="0" max="100" step="any" value={form.customsDutyRate} onChange={event => update("customsDutyRate", event.target.value)}/></label>
              <label>부가세율 (%)<input type="number" min="0" max="100" step="any" value={form.vatRate} onChange={event => update("vatRate", event.target.value)}/></label>
              <label>세율 출처<SourceSelect value={form.taxRateSource} onChange={event => update("taxRateSource", event.target.value)}/></label>
              <label>부가세 관점<select value={form.vatTreatment} onChange={event => update("vatTreatment", event.target.value)}>
                {Object.entries(vatLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
              </select></label>
            </div>
          </fieldset>
          <fieldset className="cost-fieldset"><legend>판매 가정 (원)</legend>
            <div className="form-grid four">
              <label>목표 판매가<input type="number" min="0" step="0.01" value={form.targetSellingPriceKrw} onChange={event => update("targetSellingPriceKrw", event.target.value)}/></label>
              <label>판매수수료율 (%)<input type="number" min="0" max="100" step="any" value={form.sellingFeeRate} onChange={event => update("sellingFeeRate", event.target.value)}/></label>
              <label>건별 변동비<input type="number" min="0" step="0.01" value={form.variableCostPerUnitKrw} onChange={event => update("variableCostPerUnitKrw", event.target.value)}/></label>
              <label>판매값 출처<SourceSelect value={form.salesAssumptionSource} onChange={event => update("salesAssumptionSource", event.target.value)}/></label>
            </div>
          </fieldset>
          <button className="button primary" disabled={saving}>{saving ? "계산 저장 중" : form.previousScenarioId ? "새 버전 계산·저장" : "계산·저장"}</button>
        </form>}
    </section>}
    <section className="panel cost-ledger"><div className="panel-header"><div><h2>원가 시나리오 비교</h2>
      <p className="muted">화면 표시값이 아니라 저장된 원본 decimal 입력으로 계산한 결과입니다.</p></div></div>
      {loading ? <LoadingState/> : scenarios.length === 0 ? <EmptyState title="저장한 원가 시나리오가 없습니다." description="수량과 환율을 달리해 첫 비교안을 만들어 보세요."/> :
        <div className="table-scroll"><table><thead><tr><th>상태</th><th>시나리오</th><th>견적·상품</th><th>수량</th><th>필요 현금</th><th>단위원가</th><th>공헌이익/개</th><th>입력 근거</th><th>다음 행동</th></tr></thead><tbody>
          {scenarios.map(scenario => <tr key={scenario.scenarioId}>
            <td><StatusBadge value={scenario.calculationStatus}/><small>{scenario.calculationRuleVersion}</small></td>
            <td><strong>{scenario.scenarioName}</strong><small>버전 {scenario.revisionNumber}</small></td>
            <td>{scenario.supplierName}<small>{scenario.quoteNumber || "번호 미입력"} R{scenario.quoteRevisionNumber} · {scenario.productName}</small></td>
            <td>{scenario.sellableQuantity}<small>주문 {scenario.orderQuantity} / 제외 {scenario.excludedQuantity}</small></td>
            <td>{money(scenario.totalCashRequiredKrw)}<small>부가세 {money(scenario.vatKrw)}</small></td>
            <td><strong>{money(scenario.unitLandedCostKrw)}</strong><small>{vatLabels[scenario.vatTreatment]}</small></td>
            <td>{money(scenario.contributionMarginPerUnitKrw)}</td>
            <td>{sourceLabels[scenario.exchangeRateSource] || "환율 미확인"}<small>{sourceLabels[scenario.taxRateSource] || "세율 미확인"}</small></td>
            <td>{editable && !scenariosWithNextRevision.has(scenario.scenarioId)
              ? <button className="button secondary" onClick={() => startRevision(scenario)}>새 버전</button> : "—"}</td>
          </tr>)}
        </tbody></table></div>}
    </section>
  </>;
}
