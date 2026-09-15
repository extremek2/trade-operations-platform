import { useCallback, useEffect, useMemo, useState } from "react";
import { getBusinessPartners } from "../api/partnerApi";
import { getProducts } from "../api/productApi";
import {
  confirmSupplierOfferDraft, createPurchaseSelection, createSupplierOfferDraft,
  excludeSupplierOfferLine, getPurchaseSelections, getSupplierOfferDrafts, reviewSupplierOfferLine,
} from "../api/supplierOfferApi";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import { useAuth } from "../context/AuthContext";

const editableRoles = ["OWNER", "ADMIN", "OPERATOR"];
const initialForm = { supplierId: "", sourceReference: "", originalText: "", currency: "JPY", linesText: "" };
const optionalNumber = value => value === "" ? undefined : Number(value);
const optionalText = value => value.trim() || undefined;

function ReviewLine({ draft, line, editable, saving, run }) {
  const [form, setForm] = useState({
    reviewedName: line.reviewedName || line.originalName,
    supplierSku: line.supplierSku || "", quantityUnit: line.quantityUnit || "",
    minimumQuantity: line.minimumQuantity == null ? "" : String(line.minimumQuantity),
    unitPrice: line.unitPrice == null ? "" : String(line.unitPrice),
    countryOfOrigin: line.countryOfOrigin || "", notes: line.notes || "",
  });
  const update = (field, value) => setForm(current => ({ ...current, [field]: value }));
  const review = async event => {
    event.preventDefault();
    await run(() => reviewSupplierOfferLine(draft.draftId, line.lineNumber, {
      version: draft.version, reviewedName: form.reviewedName,
      supplierSku: optionalText(form.supplierSku), quantityUnit: optionalText(form.quantityUnit),
      minimumQuantity: optionalNumber(form.minimumQuantity), unitPrice: optionalNumber(form.unitPrice),
      countryOfOrigin: optionalText(form.countryOfOrigin), notes: optionalText(form.notes),
    }));
  };
  return <fieldset className="quote-line-editor">
    <legend>원본 행 {line.lineNumber} · {line.status === "PENDING" ? "확인 대기" : line.status === "EXCLUDED" ? "제외" : "확인됨"}</legend>
    <p><strong>{line.originalName}</strong> <small>{line.sourceLocation || "위치 미입력"}</small></p>
    {editable && draft.status === "REVIEW_REQUIRED" ? <form className="setup-form" onSubmit={review}>
      <div className="form-grid two">
        <label>확인 상품명<input required maxLength="500" value={form.reviewedName} onChange={event => update("reviewedName", event.target.value)}/></label>
        <label>공급처 SKU <span className="optional">선택</span><input maxLength="100" value={form.supplierSku} onChange={event => update("supplierSku", event.target.value)}/></label>
      </div>
      <div className="form-grid four">
        <label>MOQ <span className="optional">미확인 가능</span><input type="number" min="0.000001" step="any" value={form.minimumQuantity} onChange={event => update("minimumQuantity", event.target.value)}/></label>
        <label>수량 단위<input maxLength="30" value={form.quantityUnit} onChange={event => update("quantityUnit", event.target.value)}/></label>
        <label>단가 <span className="optional">미확인 가능</span><input type="number" min="0" step="0.0001" value={form.unitPrice} onChange={event => update("unitPrice", event.target.value)}/></label>
        <label>원산지 <span className="optional">선택</span><input pattern="[A-Za-z]{2}" maxLength="2" value={form.countryOfOrigin} onChange={event => update("countryOfOrigin", event.target.value.toUpperCase())}/></label>
      </div>
      <label>메모 <span className="optional">선택</span><input maxLength="1000" value={form.notes} onChange={event => update("notes", event.target.value)}/></label>
      <div className="form-actions">
        <button className="button secondary" disabled={saving}>이 행 확인</button>
        <button type="button" className="button ghost" disabled={saving} onClick={() => run(() => excludeSupplierOfferLine(draft.draftId, line.lineNumber, draft.version))}>이 행 제외</button>
      </div>
    </form> : <p>{line.status === "EXCLUDED" ? "제외된 행입니다." : line.reviewedName ? `${line.reviewedName} · ${line.quantityUnit || "단위 미확인"} · ${line.unitPrice == null ? "단가 미확인" : `${draft.currency} ${line.unitPrice}`}` : "확인 대기"}</p>}
  </fieldset>;
}

function SelectionForm({ draft, selectedNumbers, products, saving, run }) {
  const available = draft.lines.filter(line => line.status === "CONFIRMED" && !selectedNumbers.has(line.lineNumber));
  const [choices, setChoices] = useState({});
  const update = (number, changes) => setChoices(current => ({
    ...current, [number]: { ...current[number], ...changes },
  }));
  const submit = async event => {
    event.preventDefault();
    const lines = available.filter(line => choices[line.lineNumber]?.checked).map(line => ({
      lineNumber: line.lineNumber,
      desiredQuantity: Number(choices[line.lineNumber]?.quantity || line.minimumQuantity || 1),
      ...(choices[line.lineNumber]?.existingProductId && { existingProductId: choices[line.lineNumber].existingProductId }),
    }));
    if (lines.length) await run(() => createPurchaseSelection(draft.draftId, { version: draft.version, lines }));
  };
  if (!available.length) return <p className="muted">선택 가능한 확인 행이 없습니다.</p>;
  return <form className="setup-form" aria-label={`${draft.supplierName} 구매 품목 선택`} onSubmit={submit}>
    <p className="muted">선택한 행만 상품·작성 중 견적으로 옮깁니다. 발주 승인과 실제 결제는 이 화면에서 처리하지 않습니다.</p>
    {available.map(line => <fieldset className="quote-line-editor" key={line.lineNumber}>
      <legend>확인 행 {line.lineNumber} · {line.reviewedName}</legend>
      <label><input type="checkbox" disabled={!line.quantityUnit} checked={Boolean(choices[line.lineNumber]?.checked)} onChange={event => update(line.lineNumber, { checked: event.target.checked })}/> 구매 후보로 선택</label>
      {!line.quantityUnit && <p className="muted">수량 단위가 미확인이라 이 행은 아직 선택할 수 없습니다.</p>}
      <p>{line.minimumQuantity == null ? "MOQ 미확인" : `MOQ ${line.minimumQuantity} ${line.quantityUnit || ""}`} · {line.unitPrice == null ? "단가 미확인" : `${draft.currency} ${line.unitPrice}`}</p>
      <div className="form-grid two">
        <label>희망 수량<input type="number" min={line.minimumQuantity || "0.000001"} step="any" disabled={!choices[line.lineNumber]?.checked}
          value={choices[line.lineNumber]?.quantity ?? String(line.minimumQuantity || 1)}
          onChange={event => update(line.lineNumber, { quantity: event.target.value })}/></label>
        <label>기존 상품 연결 <span className="optional">비우면 신규 등록</span>
          <select disabled={!choices[line.lineNumber]?.checked} value={choices[line.lineNumber]?.existingProductId || ""}
            onChange={event => update(line.lineNumber, { existingProductId: event.target.value })}>
            <option value="">새 상품 만들기</option>
            {products.filter(product => ["SOURCING", "REVIEWING"].includes(product.status)).map(product =>
              <option key={product.productId} value={product.productId}>{product.name}</option>)}
          </select>
        </label>
      </div>
    </fieldset>)}
    <button className="button primary" disabled={saving || !available.some(line => choices[line.lineNumber]?.checked)}>선택 행을 견적으로 넘기기</button>
  </form>;
}

export default function SupplierOfferPage({ navigate }) {
  const { user } = useAuth();
  const editable = editableRoles.includes(user?.role);
  const [suppliers, setSuppliers] = useState([]);
  const [products, setProducts] = useState([]);
  const [drafts, setDrafts] = useState([]);
  const [selections, setSelections] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [partnerRows, productRows, draftRows, selectionRows] = await Promise.all([
        getBusinessPartners(), getProducts(), getSupplierOfferDrafts(), getPurchaseSelections(),
      ]);
      setSuppliers(partnerRows.filter(item => item.roles.includes("SUPPLIER")));
      setProducts(productRows); setDrafts(draftRows); setSelections(selectionRows);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const selectedByDraft = useMemo(() => {
    const map = new Map();
    selections.forEach(selection => {
      const numbers = map.get(selection.draftId) || new Set();
      selection.lines.forEach(line => numbers.add(line.offerLineNumber));
      map.set(selection.draftId, numbers);
    });
    return map;
  }, [selections]);
  const run = async action => {
    setSaving(true); setError("");
    try { await action(); await load(); return true; }
    catch (err) { setError(err.message); return false; }
    finally { setSaving(false); }
  };
  const submit = async event => {
    event.preventDefault();
    const lines = form.linesText.split(/\r?\n/).map(value => value.trim()).filter(Boolean)
      .map(originalName => ({ originalName }));
    if (!lines.length) { setError("원본 상품 행을 한 줄 이상 입력해 주세요."); return; }
    const created = await run(() => createSupplierOfferDraft({ supplierId: form.supplierId,
      sourceReference: optionalText(form.sourceReference), originalText: form.originalText,
      currency: form.currency, lines }));
    if (created) setForm(current => ({ ...initialForm, supplierId: current.supplierId }));
  };
  const update = (field, value) => setForm(current => ({ ...current, [field]: value }));

  return <>
    <header className="page-header"><div><span className="eyebrow">SUPPLIER OFFER</span><h1>공급 상품 제안</h1>
      <p>원본을 보존하고 추출값을 확인한 뒤 살 품목만 견적 원장으로 넘깁니다.</p></div></header>
    <ErrorMessage message={error}/>
    {editable && <section className="panel"><div className="panel-header"><div><h2>수동 제안 등록</h2>
      <p className="muted">파일·URL 자동 추출 전에는 받은 내용을 직접 입력합니다. URL은 참고 위치로만 저장합니다.</p></div></div>
      {suppliers.length === 0 ? <EmptyState title="공급처가 없습니다." description="공급 견적 화면에서 공급처를 먼저 등록하세요."/> :
        <form className="setup-form" aria-label="수동 공급 제안 등록" onSubmit={submit}>
          <div className="form-grid two">
            <label>공급처<select required value={form.supplierId} onChange={event => update("supplierId", event.target.value)}>
              <option value="">선택</option>{suppliers.map(item => <option key={item.businessPartnerId} value={item.businessPartnerId}>{item.name}</option>)}
            </select></label>
            <label>통화<input required pattern="[A-Za-z]{3}" maxLength="3" value={form.currency} onChange={event => update("currency", event.target.value.toUpperCase())}/></label>
          </div>
          <label>원본 위치 <span className="optional">선택</span><input maxLength="2000" value={form.sourceReference} onChange={event => update("sourceReference", event.target.value)}/></label>
          <label>받은 원본 내용<textarea required maxLength="65536" value={form.originalText} onChange={event => update("originalText", event.target.value)}/></label>
          <label>원본 상품명 · 한 줄에 한 행<textarea required value={form.linesText} onChange={event => update("linesText", event.target.value)}/></label>
          <button className="button primary" disabled={saving}>검토 초안 만들기</button>
        </form>}
    </section>}
    <section className="panel"><div className="panel-header"><div><h2>제안 검토 원장</h2><p className="muted">추출 확인은 구매 선택과 별도입니다.</p></div></div>
      {loading ? <LoadingState/> : drafts.length === 0 ? <EmptyState title="등록한 공급 제안이 없습니다." description="원본 내용을 수동으로 등록해 첫 검토 초안을 만드세요."/> :
        drafts.map(draft => <article className="quote-entry" key={draft.draftId} aria-label={`${draft.supplierName} 제안 ${draft.status}`}>
          <div className="quote-entry-header"><div><h3>{draft.supplierName}</h3><p>{draft.status === "CONFIRMED" ? "추출 확인됨" : "추출 확인 대기"} · {draft.currency}</p></div>
            {editable && draft.status === "REVIEW_REQUIRED" && <button className="button secondary" disabled={saving || draft.lines.some(line => line.status === "PENDING")}
              onClick={() => run(() => confirmSupplierOfferDraft(draft.draftId, draft.version))}>추출 확인</button>}
          </div>
          <details><summary>원본 내용 보기 · {draft.sourceReference || "수동 입력"}</summary><pre>{draft.originalText}</pre></details>
          {draft.lines.map(line => <ReviewLine key={`${draft.draftId}:${line.lineNumber}`} draft={draft} line={line}
            editable={editable} saving={saving} run={run}/>)}
          {editable && draft.status === "CONFIRMED" && <SelectionForm draft={draft}
            selectedNumbers={selectedByDraft.get(draft.draftId) || new Set()} products={products} saving={saving} run={run}/>}
        </article>)}
    </section>
    <section className="panel"><div className="panel-header"><div><h2>구매 후보 선택 이력</h2><p className="muted">견적을 수신 처리하면 예상 원가 화면에서 희망 수량으로 계산할 수 있습니다.</p></div></div>
      {loading ? <LoadingState/> : selections.length === 0 ? <EmptyState title="선택한 품목이 없습니다." description="추출 확인된 행 중 구매 후보를 선택하세요."/> :
        <div className="quote-list">{selections.map(selection => <article className="quote-entry" key={selection.selectionId}>
          <h3>{selection.quoteNumber}</h3><p>견적 {selection.quoteStatus} · {selection.currency}</p>
          <ul>{selection.lines.map(line => <li key={line.offerLineNumber}>{line.productName} · 희망 {line.desiredQuantity} {drafts.find(draft => draft.draftId === selection.draftId)?.lines.find(item => item.lineNumber === line.offerLineNumber)?.quantityUnit || ""}</li>)}</ul>
          <div className="form-actions"><button className="button secondary" onClick={() => navigate("/sourcing")}>견적 검토로 이동</button>
            <button className="button ghost" onClick={() => navigate("/costing")}>예상 원가로 이동</button></div>
        </article>)}</div>}
    </section>
  </>;
}
