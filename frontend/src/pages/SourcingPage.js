import { useCallback, useEffect, useMemo, useState } from "react";
import { createBusinessPartner, getBusinessPartners } from "../api/partnerApi";
import { getProducts } from "../api/productApi";
import {
  changeQuoteStatus,
  createQuote,
  createQuoteRevision,
  getQuotes,
  updateQuote,
} from "../api/quoteApi";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import StatusBadge, { displayLabel } from "../components/StatusBadge";
import { useAuth } from "../context/AuthContext";

const initialSupplier = { name: "" };
const editableRoles = ["OWNER", "ADMIN", "OPERATOR"];
const statusTransitions = {
  DRAFT: ["RECEIVED", "REJECTED"],
  RECEIVED: ["SELECTED", "EXPIRED", "REJECTED"],
  SELECTED: [],
  EXPIRED: [],
  REJECTED: [],
};

const emptyLine = () => ({
  productId: "", supplierSku: "", description: "", minimumQuantity: "",
  quantityUnit: "EA", unitPrice: "", countryOfOrigin: "", leadTimeDays: "", notes: "",
});
const emptyQuote = () => ({
  supplierId: "", quoteNumber: "", currency: "KRW", quotedAt: "", validUntil: "", notes: "",
  lines: [emptyLine()],
});
const valueOrEmpty = value => value == null ? "" : String(value);
const optionalNumber = value => value === "" ? undefined : Number(value);
const optionalText = value => value ? value : undefined;

function linePayload(line) {
  return {
    productId: line.productId,
    description: line.description,
    supplierSku: optionalText(line.supplierSku),
    minimumQuantity: optionalNumber(line.minimumQuantity),
    quantityUnit: optionalText(line.quantityUnit),
    unitPrice: optionalNumber(line.unitPrice),
    countryOfOrigin: optionalText(line.countryOfOrigin),
    leadTimeDays: optionalNumber(line.leadTimeDays),
    notes: optionalText(line.notes),
  };
}

function quotePayload(form) {
  return {
    currency: form.currency,
    quotedAt: optionalText(form.quotedAt),
    validUntil: optionalText(form.validUntil),
    notes: optionalText(form.notes),
    lines: form.lines.map(linePayload),
  };
}

function formFromQuote(quote) {
  return {
    supplierId: quote.supplierId,
    quoteNumber: quote.quoteNumber || "",
    currency: quote.currency,
    quotedAt: quote.quotedAt || "",
    validUntil: quote.validUntil || "",
    notes: quote.notes || "",
    lines: quote.lines.map(line => ({
      productId: line.productId,
      supplierSku: line.supplierSku || "",
      description: line.description,
      minimumQuantity: valueOrEmpty(line.minimumQuantity),
      quantityUnit: line.quantityUnit || "",
      unitPrice: valueOrEmpty(line.unitPrice),
      countryOfOrigin: line.countryOfOrigin || "",
      leadTimeDays: valueOrEmpty(line.leadTimeDays),
      notes: line.notes || "",
    })),
  };
}

function QuoteFields({ form, setForm, products, suppliers, includeSupplier = false, lockQuoteNumber = false }) {
  const updateLine = (index, changes) => setForm(current => ({
    ...current,
    lines: current.lines.map((line, lineIndex) => lineIndex === index ? { ...line, ...changes } : line),
  }));
  const addLine = () => setForm(current => ({ ...current, lines: [...current.lines, emptyLine()] }));
  const removeLine = index => setForm(current => ({
    ...current,
    lines: current.lines.filter((_, lineIndex) => lineIndex !== index),
  }));

  return <>
    {includeSupplier && <label>공급처
      <select required value={form.supplierId} onChange={event => setForm(current => ({ ...current, supplierId: event.target.value }))}>
        <option value="">선택</option>
        {suppliers.map(item => <option key={item.businessPartnerId} value={item.businessPartnerId}>{item.name}</option>)}
      </select>
    </label>}
    <div className="form-grid two">
      <label>견적번호 <span className="optional">선택</span>
        <input disabled={lockQuoteNumber} maxLength="100" value={form.quoteNumber}
          onChange={event => setForm(current => ({ ...current, quoteNumber: event.target.value }))}/>
      </label>
      <label>통화
        <input required pattern="[A-Za-z]{3}" maxLength="3" value={form.currency}
          onChange={event => setForm(current => ({ ...current, currency: event.target.value.toUpperCase() }))}/>
      </label>
    </div>
    <div className="form-grid two">
      <label>견적일 <span className="optional">선택</span>
        <input type="date" value={form.quotedAt} onChange={event => setForm(current => ({ ...current, quotedAt: event.target.value }))}/>
      </label>
      <label>유효일 <span className="optional">선택</span>
        <input type="date" min={form.quotedAt || undefined} value={form.validUntil}
          onChange={event => setForm(current => ({ ...current, validUntil: event.target.value }))}/>
      </label>
    </div>
    <label>견적 메모 <span className="optional">선택</span>
      <textarea maxLength="2000" value={form.notes} onChange={event => setForm(current => ({ ...current, notes: event.target.value }))}/>
    </label>
    <div className="quote-lines-heading">
      <strong>견적 품목</strong>
      <button type="button" className="button secondary" onClick={addLine}>품목 행 추가</button>
    </div>
    {form.lines.map((line, index) => <fieldset className="quote-line-editor" key={index}>
      <legend>품목 {index + 1}</legend>
      <div className="form-grid two">
        <label>상품
          <select required value={line.productId} onChange={event => updateLine(index, { productId: event.target.value })}>
            <option value="">선택</option>
            {products.map(item => <option key={item.productId} value={item.productId}>{item.name}</option>)}
          </select>
        </label>
        <label>공급처 SKU <span className="optional">선택</span>
          <input maxLength="100" value={line.supplierSku} onChange={event => updateLine(index, { supplierSku: event.target.value })}/>
        </label>
      </div>
      <label>품목 설명
        <input required maxLength="500" value={line.description} onChange={event => updateLine(index, { description: event.target.value })}/>
      </label>
      <div className="form-grid four">
        <label>MOQ<input type="number" min="0.000001" step="any" value={line.minimumQuantity} onChange={event => updateLine(index, { minimumQuantity: event.target.value })}/></label>
        <label>단위<input maxLength="30" value={line.quantityUnit} onChange={event => updateLine(index, { quantityUnit: event.target.value })}/></label>
        <label>단가<input type="number" min="0" step="0.0001" value={line.unitPrice} onChange={event => updateLine(index, { unitPrice: event.target.value })}/></label>
        <label>납기(일)<input type="number" min="0" step="1" value={line.leadTimeDays} onChange={event => updateLine(index, { leadTimeDays: event.target.value })}/></label>
      </div>
      <div className="form-grid two">
        <label>원산지 국가코드 <span className="optional">선택</span>
          <input pattern="[A-Za-z]{2}" maxLength="2" value={line.countryOfOrigin}
            onChange={event => updateLine(index, { countryOfOrigin: event.target.value.toUpperCase() })}/>
        </label>
        <label>품목 메모 <span className="optional">선택</span>
          <input maxLength="1000" value={line.notes} onChange={event => updateLine(index, { notes: event.target.value })}/>
        </label>
      </div>
      {form.lines.length > 1 && <button type="button" className="button ghost line-remove" onClick={() => removeLine(index)}>이 품목 제거</button>}
    </fieldset>)}
  </>;
}

function QuoteLedgerEntry({ quote, previous, hasNextRevision, editable, activeEditor, beginEditor, setEditor,
  products, saving, submitEditor, transitionStatus }) {
  const isEditing = activeEditor?.quote.quoteId === quote.quoteId;
  const transitions = statusTransitions[quote.status] || [];
  const previousLabel = previous
    ? `${previous.quoteNumber || "번호 미입력"} · 개정 ${previous.revisionNumber}`
    : quote.previousQuoteId ? quote.previousQuoteId : "최초 견적";

  return <article className="quote-entry" aria-label={`${quote.supplierName} ${quote.quoteNumber || "번호 미입력"} 개정 ${quote.revisionNumber}`}>
    <div className="quote-entry-header">
      <div>
        <div className="quote-title-row"><StatusBadge value={quote.status}/><h3>{quote.supplierName}</h3></div>
        <strong>{quote.quoteNumber || "견적번호 미입력"}</strong>
      </div>
      <dl className="quote-meta">
        <div><dt>개정</dt><dd>{quote.revisionNumber}</dd></div>
        <div><dt>견적일</dt><dd>{quote.quotedAt || "미입력"}</dd></div>
        <div><dt>유효일</dt><dd>{quote.validUntil || "미입력"}</dd></div>
        <div><dt>이전 개정본</dt><dd title={quote.previousQuoteId || undefined}>{previousLabel}</dd></div>
      </dl>
      {editable && <div className="quote-actions">
        {quote.status === "DRAFT"
          ? <button type="button" className="button secondary" disabled={saving || isEditing} onClick={() => beginEditor("edit", quote)}>내용 수정</button>
          : !hasNextRevision && <button type="button" className="button secondary" disabled={saving || isEditing} onClick={() => beginEditor("revision", quote)}>새 개정본</button>}
        {transitions.map(status => <button type="button" className={status === "REJECTED" ? "button danger" : "button ghost"}
          disabled={saving} key={status} onClick={() => transitionStatus(quote, status)}>{displayLabel(status)} 처리</button>)}
      </div>}
    </div>
    {quote.notes && <p className="quote-notes">{quote.notes}</p>}
    {isEditing && <form className="quote-editor" aria-label={activeEditor.mode === "edit" ? "견적 내용 수정" : "새 개정본 작성"} onSubmit={submitEditor}>
      <div className="quote-editor-heading">
        <div><h4>{activeEditor.mode === "edit" ? "견적 내용 수정" : `새 개정본 작성 · 개정 ${quote.revisionNumber + 1}`}</h4>
          <p className="muted">기존 품목 행을 모두 불러왔습니다. 변경할 값만 고친 뒤 저장하세요.</p></div>
        <button type="button" className="button ghost" onClick={() => setEditor(null)}>취소</button>
      </div>
      <QuoteFields form={activeEditor.form} setForm={value => setEditor(current => ({
        ...current,
        form: typeof value === "function" ? value(current.form) : value,
      }))} products={products} lockQuoteNumber={activeEditor.mode === "revision"}/>
      <div className="form-actions"><button className="button primary" disabled={saving}>{saving ? "저장 중" : activeEditor.mode === "edit" ? "수정 저장" : "개정본 저장"}</button></div>
    </form>}
    <div className="table-scroll"><table className="quote-table"><thead><tr><th>상품</th><th>공급처 SKU</th><th>MOQ</th><th>단가</th><th>원산지</th><th>납기</th></tr></thead><tbody>
      {quote.lines.map(line => <tr key={line.lineNumber}><td><strong>{line.productName}</strong><small>{line.description}</small></td><td>{line.supplierSku || "미입력"}</td><td>{line.minimumQuantity == null ? "미확인" : `${line.minimumQuantity} ${line.quantityUnit || ""}`}</td><td>{line.unitPrice == null ? "미확인" : `${quote.currency} ${line.unitPrice}`}</td><td>{line.countryOfOrigin || "미확인"}</td><td>{line.leadTimeDays == null ? "미확인" : `${line.leadTimeDays}일`}</td></tr>)}
    </tbody></table></div>
  </article>;
}

export default function SourcingPage() {
  const { user } = useAuth();
  const editable = editableRoles.includes(user?.role);
  const [suppliers, setSuppliers] = useState([]);
  const [products, setProducts] = useState([]);
  const [quotes, setQuotes] = useState([]);
  const [supplierForm, setSupplierForm] = useState(initialSupplier);
  const [createForm, setCreateForm] = useState(emptyQuote);
  const [editor, setEditor] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const sourcingProducts = useMemo(
    () => products.filter(product => ["SOURCING", "REVIEWING"].includes(product.status)), [products]);
  const quotesById = useMemo(() => new Map(quotes.map(quote => [quote.quoteId, quote])), [quotes]);
  const quotesWithNextRevision = useMemo(() => new Set(quotes.map(quote => quote.previousQuoteId).filter(Boolean)), [quotes]);

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [partnerRows, productRows, quoteRows] = await Promise.all([getBusinessPartners(), getProducts(), getQuotes()]);
      setSuppliers(partnerRows.filter(partner => partner.roles.includes("SUPPLIER")));
      setProducts(productRows);
      setQuotes(quoteRows);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const submitSupplier = async event => {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await createBusinessPartner({ name: supplierForm.name, roles: ["SUPPLIER"] });
      setSupplierForm(initialSupplier); await load();
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  };

  const submitQuote = async event => {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await createQuote({
        ...quotePayload(createForm),
        supplierId: createForm.supplierId,
        quoteNumber: optionalText(createForm.quoteNumber),
      });
      setCreateForm(current => ({ ...emptyQuote(), supplierId: current.supplierId }));
      await load();
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  };

  const beginEditor = (mode, quote) => {
    setError("");
    setEditor({ mode, quote, form: formFromQuote(quote) });
  };

  const submitEditor = async event => {
    event.preventDefault(); setSaving(true); setError("");
    const payload = { ...quotePayload(editor.form), version: editor.quote.version };
    try {
      if (editor.mode === "edit") {
        await updateQuote(editor.quote.quoteId, { ...payload, quoteNumber: optionalText(editor.form.quoteNumber) });
      } else {
        await createQuoteRevision(editor.quote.quoteId, payload);
      }
      setEditor(null); await load();
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  };

  const transitionStatus = async (quote, status) => {
    setSaving(true); setError("");
    try {
      await changeQuoteStatus(quote.quoteId, { status, version: quote.version });
      if (editor?.quote.quoteId === quote.quoteId) setEditor(null);
      await load();
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  };

  return <>
    <header className="page-header"><div><span className="eyebrow">SOURCING</span><h1>공급 견적</h1><p>공급처가 제시한 조건을 상품 원장에 연결해 비교합니다.</p></div></header>
    <ErrorMessage message={error}/>
    {editable && <div className="detail-grid sourcing-forms">
      <section className="panel"><div className="panel-header"><div><h2>공급처 등록</h2><p className="muted">같은 거래처 원장에 공급처 역할을 부여합니다.</p></div></div>
        <form className="setup-form" onSubmit={submitSupplier}>
          <label>공급처명<input required maxLength="200" value={supplierForm.name} onChange={event => setSupplierForm({ name: event.target.value })}/></label>
          <button className="button primary" disabled={saving}>공급처 등록</button>
        </form>
      </section>
      <section className="panel"><div className="panel-header"><div><h2>견적 입력</h2><p className="muted">모르는 단가와 수량은 비워 둘 수 있습니다.</p></div></div>
        {suppliers.length === 0 || sourcingProducts.length === 0
          ? <EmptyState title="견적 입력 준비가 필요합니다." description="공급처를 등록하고 상품 화면에서 공급 조사를 시작하세요."/>
          : <form className="setup-form" onSubmit={submitQuote}>
            <QuoteFields form={createForm} setForm={setCreateForm} products={sourcingProducts} suppliers={suppliers} includeSupplier/>
            <button className="button primary" disabled={saving}>{saving ? "저장 중" : "견적 저장"}</button>
          </form>}
      </section>
    </div>}
    <section className="panel quote-ledger"><div className="panel-header"><div><h2>견적 비교 원장</h2><p className="muted">각 견적의 개정 이력과 품목 조건을 한 묶음으로 비교합니다.</p></div></div>
      {loading ? <LoadingState/> : quotes.length === 0 ? <EmptyState title="등록한 공급 견적이 없습니다." description="같은 상품에 여러 공급처 견적을 연결해 비교하세요."/> :
        <div className="quote-list">{quotes.map(quote => <QuoteLedgerEntry key={quote.quoteId} quote={quote}
          previous={quotesById.get(quote.previousQuoteId)} hasNextRevision={quotesWithNextRevision.has(quote.quoteId)}
          editable={editable} activeEditor={editor} beginEditor={beginEditor} setEditor={setEditor}
          products={products} saving={saving} submitEditor={submitEditor} transitionStatus={transitionStatus}/>)}</div>}
    </section>
  </>;
}
