import { useCallback, useEffect, useMemo, useState } from "react";
import { getBusinessPartners } from "../api/partnerApi";
import { getProducts } from "../api/productApi";
import {
  confirmSupplierOfferDraft, createDocumentDraft, createPurchaseSelection, createSupplierOfferDraft,
  excludeSupplierOfferLine, getPurchaseSelections, getSupplierDocumentContent, getSupplierDocuments,
  getSupplierOfferDrafts, importSupplierDocument, importSupplierOfferFile,
  reviewSupplierOfferDraft, reviewSupplierOfferLine,
} from "../api/supplierOfferApi";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import { useAuth } from "../context/AuthContext";

const editableRoles = ["OWNER", "ADMIN", "OPERATOR"];
const initialForm = { supplierId: "", sourceReference: "", originalText: "", currency: "JPY", linesText: "" };
const initialFileForm = { supplierId: "", sourceReference: "", currency: "JPY", file: null };
const initialDocumentForm = { supplierId: "", sourceReference: "", file: null };
const optionalNumber = value => value === "" ? undefined : Number(value);
const optionalText = value => value.trim() || undefined;

const documentSources = ["PDF", "IMAGE"];
const initialReviewRow = line => ({
  included: line.status === "CONFIRMED" || (line.status === "PENDING" && Boolean(line.supplierSku && line.reviewedName)),
  checked: line.status === "CONFIRMED",
  reviewedName: line.reviewedName || "", supplierSku: line.supplierSku || "",
  minimumQuantity: line.minimumQuantity == null ? "" : String(line.minimumQuantity),
  quantityUnit: line.quantityUnit || "", unitPrice: line.unitPrice == null ? "" : String(line.unitPrice),
  countryOfOrigin: line.countryOfOrigin || "", notes: line.notes || "",
});

function DocumentReviewWorkspace({ draft, editable, saving, run }) {
  const [previewUrl, setPreviewUrl] = useState("");
  const [previewError, setPreviewError] = useState("");
  const [rows, setRows] = useState(() => Object.fromEntries(draft.lines.map(line => [line.lineNumber, initialReviewRow(line)])));
  const [showExcluded, setShowExcluded] = useState(false);
  useEffect(() => {
    let active = true;
    let url = "";
    getSupplierDocumentContent(draft.sourceArtifactId).then(blob => {
      if (!active) return;
      url = URL.createObjectURL(blob);
      setPreviewUrl(url);
    }).catch(error => { if (active) setPreviewError(error.message); });
    return () => { active = false; if (url) URL.revokeObjectURL(url); };
  }, [draft.sourceArtifactId]);
  const change = (number, values) => setRows(current => ({ ...current, [number]: { ...current[number], ...values,
    ...(!Object.hasOwn(values, "checked") ? { checked: false } : {}) } }));
  const included = draft.lines.filter(line => rows[line.lineNumber].included);
  const excludedCount = draft.lines.length - included.length;
  const visibleLines = draft.lines.filter(line => rows[line.lineNumber].included || showExcluded);
  const outstanding = included.filter(line => {
    const row = rows[line.lineNumber];
    return !row.checked || !row.reviewedName.trim() || !row.minimumQuantity || Number(row.minimumQuantity) <= 0 || !row.quantityUnit.trim();
  });
  const submit = async event => {
    event.preventDefault();
    if (!included.length || outstanding.length) return;
    await run(() => reviewSupplierOfferDraft(draft.draftId, {
      version: draft.version,
      lines: draft.lines.map(line => {
        const row = rows[line.lineNumber];
        return row.included ? { lineNumber: line.lineNumber, decision: "CONFIRM",
          reviewedName: row.reviewedName.trim(), supplierSku: optionalText(row.supplierSku),
          minimumQuantity: Number(row.minimumQuantity), quantityUnit: row.quantityUnit.trim(),
          unitPrice: optionalNumber(row.unitPrice), countryOfOrigin: optionalText(row.countryOfOrigin),
          notes: optionalText(row.notes) } : { lineNumber: line.lineNumber, decision: "EXCLUDE" };
      }),
    }));
  };
  return <form className="document-review" aria-label={`${draft.supplierName} 문서 검토`} onSubmit={submit}>
    <div className="document-review-heading"><div><h4>원본 대조 · {draft.originalFileName}</h4>
      <p className="muted">왼쪽 원본과 오른쪽 추출값을 비교하세요. 품목을 선택하고 수량·단위를 확인한 뒤 대조 완료를 체크합니다.</p></div>
      <span className="document-review-count">품목 {included.length}개 · 확인 남음 {outstanding.length}개</span></div>
    <div className="document-review-grid">
      <section className="document-review-original" aria-label="원본 문서"><h5>원본 문서</h5>
        {previewError ? <p role="alert">원본을 불러오지 못했습니다: {previewError}</p> : previewUrl ?
          draft.sourceType === "IMAGE" ? <img src={previewUrl} alt={draft.originalFileName}/> :
            <iframe src={previewUrl} title={`${draft.originalFileName} 원본`}/> : <p className="muted">원본을 불러오는 중입니다.</p>}
      </section>
      <section className="document-review-digital" aria-label="디지털 추출 표"><div className="document-review-table-head"><h5>디지털 추출 표</h5>
        <span className="muted">행 OCR 95% 미만 또는 값 누락 시 확인 표시</span></div>
        {excludedCount > 0 && <button className="button ghost review-excluded-toggle" type="button" onClick={() => setShowExcluded(value => !value)}>
          {showExcluded ? "상품 외 문구 접기" : `상품 외 문구 ${excludedCount}행 보기`}</button>}
        <div className="document-review-table-scroll"><table className="document-review-table"><thead><tr>
          <th>품목</th><th>추출 상태</th><th>상품명</th><th>SKU</th><th>수량</th><th>단위</th><th>단가 ({draft.currency})</th><th>원산지</th><th>대조</th>
        </tr></thead><tbody>{visibleLines.map(line => {
          const row = rows[line.lineNumber];
          const lowConfidence = draft.extractionMethod === "OCR" && (line.extractionConfidence == null || Number(line.extractionConfidence) < 95);
          const missing = row.included && (!row.minimumQuantity || !row.quantityUnit || !row.unitPrice);
          const fieldClass = value => row.included && (lowConfidence || !value) ? "review-uncertain" : "";
          return <tr key={line.lineNumber} className={row.included ? "" : "review-excluded"}>
            <td data-label="품목"><label className="review-check"><input type="checkbox" checked={row.included} disabled={!editable || saving}
              onChange={event => change(line.lineNumber, { included: event.target.checked })}/><span>행 {line.lineNumber}</span></label>
              <small>{line.sourceLocation}</small></td>
            <td data-label="추출 상태">{lowConfidence ? <span className="review-warning">원본 대조 필요{line.extractionConfidence != null && ` · ${line.extractionConfidence}%`}</span>
              : draft.extractionMethod === "PDF_TEXT" ? <span className="review-confidence">텍스트 추출</span>
                : line.extractionConfidence != null ? <span className="review-confidence">행 OCR {line.extractionConfidence}%</span> : <span className="muted">직접 확인</span>}
              {missing && <small className="review-warning">수량·단위·단가 누락 확인</small>}
              {line.extractionErrors && <small className="review-warning">{line.extractionErrors}</small>}
              <small title={line.originalName}>원문: {line.originalName}</small></td>
            <td data-label="상품명"><input aria-label={`행 ${line.lineNumber} 상품명`} className={fieldClass(row.reviewedName)} required={row.included}
              maxLength="500" disabled={!row.included || !editable || saving} value={row.reviewedName}
              onChange={event => change(line.lineNumber, { reviewedName: event.target.value })}/></td>
            <td data-label="SKU"><input aria-label={`행 ${line.lineNumber} SKU`} className={fieldClass(row.supplierSku)} maxLength="100"
              disabled={!row.included || !editable || saving} value={row.supplierSku} onChange={event => change(line.lineNumber, { supplierSku: event.target.value })}/></td>
            <td data-label="수량"><input aria-label={`행 ${line.lineNumber} 수량`} className={fieldClass(row.minimumQuantity)} type="number" min="0.000001" step="any" required={row.included}
              disabled={!row.included || !editable || saving} value={row.minimumQuantity} onChange={event => change(line.lineNumber, { minimumQuantity: event.target.value, checked: false })}/></td>
            <td data-label="단위"><input aria-label={`행 ${line.lineNumber} 단위`} className={fieldClass(row.quantityUnit)} maxLength="30" required={row.included}
              disabled={!row.included || !editable || saving} value={row.quantityUnit} onChange={event => change(line.lineNumber, { quantityUnit: event.target.value, checked: false })}/></td>
            <td data-label={`단가 (${draft.currency})`}><input aria-label={`행 ${line.lineNumber} 단가`} className={fieldClass(row.unitPrice)} type="number" min="0" step="0.0001"
              disabled={!row.included || !editable || saving} value={row.unitPrice} onChange={event => change(line.lineNumber, { unitPrice: event.target.value, checked: false })}/></td>
            <td data-label="원산지"><input aria-label={`행 ${line.lineNumber} 원산지`} className={fieldClass(row.countryOfOrigin)} pattern="[A-Za-z]{2}" maxLength="2"
              disabled={!row.included || !editable || saving} value={row.countryOfOrigin} onChange={event => change(line.lineNumber, { countryOfOrigin: event.target.value.toUpperCase(), checked: false })}/></td>
            <td data-label="원본 대조"><label className="review-check"><input type="checkbox" checked={row.checked} disabled={!row.included || !editable || saving}
              onChange={event => change(line.lineNumber, { checked: event.target.checked })}/><span>행 {line.lineNumber} 대조 완료</span></label></td>
          </tr>;
        })}</tbody></table></div>
        <p className="muted">OCR 신뢰도는 글자 인식 점수이며 상품 정보의 정확성을 보증하지 않습니다. 단가가 비어 있어도 확인 후 진행할 수 있습니다.</p>
      </section>
    </div>
    {editable && <div className="document-review-actions"><span>{included.length === 0 ? "품목을 한 개 이상 선택해 주세요." : outstanding.length ? "선택한 품목의 수량·단위와 원본 대조를 완료해 주세요." : "검토를 완료할 수 있습니다."}</span>
      <button className="button primary" disabled={saving || !included.length || Boolean(outstanding.length)}>검토 완료하고 구매 후보 선택으로</button></div>}
  </form>;
}

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
    {line.extractionErrors && <div className="alert error" role="alert">자동 추출 확인 필요: {line.extractionErrors}</div>}
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
  const [documents, setDocuments] = useState([]);
  const [documentForm, setDocumentForm] = useState(initialDocumentForm);
  const [documentInputKey, setDocumentInputKey] = useState(0);
  const [documentCurrencies, setDocumentCurrencies] = useState({});
  const [documentPreview, setDocumentPreview] = useState(null);
  const [selections, setSelections] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [fileForm, setFileForm] = useState(initialFileForm);
  const [fileInputKey, setFileInputKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [partnerRows, productRows, draftRows, selectionRows, documentRows] = await Promise.all([
        getBusinessPartners(), getProducts(), getSupplierOfferDrafts(), getPurchaseSelections(), getSupplierDocuments(),
      ]);
      setSuppliers(partnerRows.filter(item => item.roles.includes("SUPPLIER")));
      setProducts(productRows); setDrafts(draftRows); setSelections(selectionRows); setDocuments(documentRows);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);
  useEffect(() => () => { if (documentPreview?.url) URL.revokeObjectURL(documentPreview.url); }, [documentPreview]);

  const selectedByDraft = useMemo(() => {
    const map = new Map();
    selections.forEach(selection => {
      const numbers = map.get(selection.draftId) || new Set();
      selection.lines.forEach(line => numbers.add(line.offerLineNumber));
      map.set(selection.draftId, numbers);
    });
    return map;
  }, [selections]);
  const ledgerDrafts = drafts.filter(draft => !documentSources.includes(draft.sourceType));
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
  const updateFile = (field, value) => setFileForm(current => ({ ...current, [field]: value }));
  const updateDocument = (field, value) => setDocumentForm(current => ({ ...current, [field]: value }));
  const submitDocument = async event => {
    event.preventDefault();
    if (!documentForm.file) { setError("PDF 또는 이미지 파일을 선택해 주세요."); return; }
    if (documentForm.file.size > 15 * 1024 * 1024) { setError("문서는 15MB 이하여야 합니다."); return; }
    const imported = await run(() => importSupplierDocument({
      supplierId: documentForm.supplierId, sourceReference: optionalText(documentForm.sourceReference),
      file: documentForm.file,
    }));
    if (imported) {
      setDocumentForm(current => ({ ...initialDocumentForm, supplierId: current.supplierId }));
      setDocumentInputKey(current => current + 1);
    }
  };
  const showDocument = async document => {
    setSaving(true); setError("");
    try {
      const blob = await getSupplierDocumentContent(document.documentId);
      setDocumentPreview({ documentId: document.documentId, url: URL.createObjectURL(blob),
        image: document.sourceType === "IMAGE" });
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  };
  const submitFile = async event => {
    event.preventDefault();
    if (!fileForm.file) { setError("가져올 CSV 또는 XLSX 파일을 선택해 주세요."); return; }
    if (fileForm.file.size > 2 * 1024 * 1024) { setError("파일은 2MB 이하여야 합니다."); return; }
    const imported = await run(() => importSupplierOfferFile({
      supplierId: fileForm.supplierId, currency: fileForm.currency,
      sourceReference: optionalText(fileForm.sourceReference), file: fileForm.file,
    }));
    if (imported) {
      setFileForm(current => ({ ...initialFileForm, supplierId: current.supplierId }));
      setFileInputKey(current => current + 1);
    }
  };

  return <>
    <header className="page-header"><div><span className="eyebrow">SUPPLIER OFFER</span><h1>공급 상품 제안</h1>
      <p>원본을 보존하고 추출값을 확인한 뒤 살 품목만 견적 원장으로 넘깁니다.</p></div></header>
    <ErrorMessage message={error}/>
    {editable && <section className="panel"><div className="panel-header"><div><h2>PDF·이미지 문서 가져오기</h2>
      <p className="muted">원본을 보관하고 텍스트를 추출합니다. 초안의 각 행은 사람이 확인하기 전까지 구매 선택에 사용할 수 없습니다.</p></div></div>
      {suppliers.length === 0 ? <EmptyState title="공급처가 없습니다." description="공급 견적 화면에서 공급처를 먼저 등록하세요."/> :
        <form className="setup-form" aria-label="공급 문서 가져오기" onSubmit={submitDocument}>
          <label>공급처<select required value={documentForm.supplierId} onChange={event => updateDocument("supplierId", event.target.value)}>
            <option value="">선택</option>{suppliers.map(item => <option key={item.businessPartnerId} value={item.businessPartnerId}>{item.name}</option>)}
          </select></label>
          <label>원본 위치 <span className="optional">선택</span><input maxLength="2000" value={documentForm.sourceReference} onChange={event => updateDocument("sourceReference", event.target.value)}/></label>
          <label>문서 파일 <span className="optional">PDF/PNG/JPEG · 최대 15MB</span><input key={documentInputKey} required type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={event => updateDocument("file", event.target.files?.[0] || null)}/></label>
          <button className="button primary" disabled={saving}>원본 보관하고 텍스트 추출</button>
        </form>}
    </section>}
    {documents.length > 0 && <section className="panel"><div className="panel-header"><div><h2>공급 문서 검토</h2>
      <p className="muted">문서에서 검토 초안을 만들면 원본과 수정 가능한 품목 표를 나란히 볼 수 있습니다.</p></div></div>
      <div className="quote-list">{documents.map(document => {
        const linkedDraft = drafts.find(draft => draft.sourceArtifactId === document.documentId);
        return <article className="quote-entry supplier-document" key={document.documentId}>
          <h3>{document.originalFileName}</h3>
          <p>{document.extractionStatus === "SUCCEEDED" ? "텍스트 추출 완료" : "추출 실패 · 원본 보관됨"}
            {document.confidence != null && (document.extractionMethod === "OCR"
              ? ` · 문서 OCR 평균 ${document.confidence}%` : " · PDF 텍스트 추출")}</p>
          {document.errorMessage && <p className="alert error">{document.errorMessage}</p>}
          <div className="form-actions">
            {!linkedDraft && <button type="button" className="button secondary" disabled={saving} onClick={() => showDocument(document)}>원본 표시</button>}
            {editable && document.extractionStatus === "SUCCEEDED" && !linkedDraft && <>
              <label>통화<input aria-label={`${document.originalFileName} 통화`} pattern="[A-Za-z]{3}" maxLength="3"
                value={documentCurrencies[document.documentId] || "JPY"}
                onChange={event => setDocumentCurrencies(current => ({ ...current,
                  [document.documentId]: event.target.value.toUpperCase() }))}/></label>
              <button type="button" className="button primary" disabled={saving || !/^[A-Z]{3}$/.test(documentCurrencies[document.documentId] || "JPY")}
                onClick={() => run(() => createDocumentDraft(document.documentId, documentCurrencies[document.documentId] || "JPY"))}>문서 검토 초안 만들기</button>
            </>}
            {linkedDraft && <span>{linkedDraft.status === "CONFIRMED" ? "검토 완료" : "검토 초안 연결됨"} · {linkedDraft.currency}</span>}
          </div>
          {!linkedDraft && <div className="supplier-document-compare">
            <div><h4>원본 문서</h4>{documentPreview?.documentId === document.documentId
              ? documentPreview.image ? <img src={documentPreview.url} alt={document.originalFileName}/>
                : <iframe title={`${document.originalFileName} 원본`} src={documentPreview.url}/>
              : <p className="muted">원본 표시를 누르면 여기에서 볼 수 있습니다.</p>}</div>
            <div><h4>추출 텍스트</h4><pre>{document.extractedText || "추출된 텍스트가 없습니다."}</pre></div>
          </div>}
          {linkedDraft?.status === "REVIEW_REQUIRED" && <DocumentReviewWorkspace draft={linkedDraft}
            editable={editable} saving={saving} run={run}/>}
          {editable && linkedDraft?.status === "CONFIRMED" && <div className="document-selection"><h4>다음 단계 · 구매 후보 선택</h4>
            <SelectionForm draft={linkedDraft} selectedNumbers={selectedByDraft.get(linkedDraft.draftId) || new Set()}
              products={products} saving={saving} run={run}/></div>}
        </article>;
      })}</div>
    </section>}
    {editable && <section className="panel"><div className="panel-header"><div><h2>제안 파일 가져오기</h2>
      <p className="muted">UTF-8 CSV 또는 XLSX의 첫 번째 표시 시트를 읽습니다. 원본은 해시와 함께 보존하며 수식은 실행하지 않습니다.</p></div></div>
      {suppliers.length === 0 ? <EmptyState title="공급처가 없습니다." description="공급 견적 화면에서 공급처를 먼저 등록하세요."/> :
        <form className="setup-form" aria-label="공급 제안 파일 가져오기" onSubmit={submitFile}>
          <div className="form-grid two">
            <label>공급처<select required value={fileForm.supplierId} onChange={event => updateFile("supplierId", event.target.value)}>
              <option value="">선택</option>{suppliers.map(item => <option key={item.businessPartnerId} value={item.businessPartnerId}>{item.name}</option>)}
            </select></label>
            <label>통화<input required pattern="[A-Za-z]{3}" maxLength="3" value={fileForm.currency} onChange={event => updateFile("currency", event.target.value.toUpperCase())}/></label>
          </div>
          <label>원본 위치 <span className="optional">선택</span><input maxLength="2000" value={fileForm.sourceReference} onChange={event => updateFile("sourceReference", event.target.value)}/></label>
          <label>제안 파일 <span className="optional">CSV/XLSX · 최대 2MB</span><input key={fileInputKey} required type="file" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={event => updateFile("file", event.target.files?.[0] || null)}/></label>
          <button className="button primary" disabled={saving}>파일 분석해 검토 초안 만들기</button>
        </form>}
    </section>}
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
    {ledgerDrafts.length > 0 && <section className="panel"><div className="panel-header"><div><h2>제안 검토 원장</h2><p className="muted">CSV·XLSX·수동 제안을 검토합니다.</p></div></div>
      {ledgerDrafts.map(draft => <article className="quote-entry" key={draft.draftId} aria-label={`${draft.supplierName} 제안 ${draft.status}`}>
          <div className="quote-entry-header"><div><h3>{draft.supplierName}</h3><p>{draft.status === "CONFIRMED" ? "추출 확인됨" : "추출 확인 대기"} · {draft.currency}</p></div>
            {editable && draft.status === "REVIEW_REQUIRED" && !documentSources.includes(draft.sourceType) && <button className="button secondary" disabled={saving || draft.lines.some(line => line.status === "PENDING")}
              onClick={() => run(() => confirmSupplierOfferDraft(draft.draftId, draft.version))}>추출 확인</button>}
          </div>
          {draft.sourceType === "MANUAL" ?
            <details><summary>원본 내용 보기 · {draft.sourceReference || "수동 입력"}</summary><pre>{draft.originalText}</pre></details> :
            <details><summary>원본 파일 정보 · {draft.originalFileName}</summary><p>{draft.sourceType} · {draft.fileSize?.toLocaleString()} bytes · SHA-256 {draft.contentHash}</p>{draft.sourceReference && <p>{draft.sourceReference}</p>}
              {draft.extractedText && <pre>{draft.extractedText}</pre>}</details>}
          {!documentSources.includes(draft.sourceType) && draft.lines.map(line => <ReviewLine key={`${draft.draftId}:${line.lineNumber}`} draft={draft} line={line}
            editable={editable} saving={saving} run={run}/>)}
          {editable && draft.status === "CONFIRMED" && <SelectionForm draft={draft}
            selectedNumbers={selectedByDraft.get(draft.draftId) || new Set()} products={products} saving={saving} run={run}/>}
        </article>)}
    </section>}
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
