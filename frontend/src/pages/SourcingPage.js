import { useCallback, useEffect, useMemo, useState } from "react";
import { createBusinessPartner, getBusinessPartners } from "../api/partnerApi";
import { getProducts } from "../api/productApi";
import { createQuote, getQuotes } from "../api/quoteApi";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import StatusBadge from "../components/StatusBadge";
import { useAuth } from "../context/AuthContext";

const initialSupplier = { name: "" };
const initialQuote = {
  supplierId: "", productId: "", quoteNumber: "", currency: "KRW", description: "",
  minimumQuantity: "", quantityUnit: "EA", unitPrice: "", countryOfOrigin: "", leadTimeDays: "",
};

const optionalNumber = value => value === "" ? undefined : Number(value);

export default function SourcingPage() {
  const { user } = useAuth();
  const editable = ["OWNER", "ADMIN", "OPERATOR"].includes(user?.role);
  const [suppliers, setSuppliers] = useState([]);
  const [products, setProducts] = useState([]);
  const [quotes, setQuotes] = useState([]);
  const [supplierForm, setSupplierForm] = useState(initialSupplier);
  const [quoteForm, setQuoteForm] = useState(initialQuote);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const sourcingProducts = useMemo(
    () => products.filter(product => ["SOURCING", "REVIEWING"].includes(product.status)), [products]);

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
    const line = {
      productId: quoteForm.productId,
      description: quoteForm.description,
      ...(quoteForm.minimumQuantity !== "" && { minimumQuantity: optionalNumber(quoteForm.minimumQuantity) }),
      ...(quoteForm.quantityUnit && { quantityUnit: quoteForm.quantityUnit }),
      ...(quoteForm.unitPrice !== "" && { unitPrice: optionalNumber(quoteForm.unitPrice) }),
      ...(quoteForm.countryOfOrigin && { countryOfOrigin: quoteForm.countryOfOrigin }),
      ...(quoteForm.leadTimeDays !== "" && { leadTimeDays: optionalNumber(quoteForm.leadTimeDays) }),
    };
    try {
      await createQuote({
        supplierId: quoteForm.supplierId,
        ...(quoteForm.quoteNumber && { quoteNumber: quoteForm.quoteNumber }),
        currency: quoteForm.currency,
        lines: [line],
      });
      setQuoteForm(current => ({ ...initialQuote, supplierId: current.supplierId, productId: current.productId }));
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
            <div className="form-grid two">
              <label>공급처<select required value={quoteForm.supplierId} onChange={event => setQuoteForm({...quoteForm, supplierId:event.target.value})}><option value="">선택</option>{suppliers.map(item => <option key={item.businessPartnerId} value={item.businessPartnerId}>{item.name}</option>)}</select></label>
              <label>상품<select required value={quoteForm.productId} onChange={event => setQuoteForm({...quoteForm, productId:event.target.value})}><option value="">선택</option>{sourcingProducts.map(item => <option key={item.productId} value={item.productId}>{item.name}</option>)}</select></label>
            </div>
            <div className="form-grid two">
              <label>견적번호 <span className="optional">선택</span><input maxLength="100" value={quoteForm.quoteNumber} onChange={event => setQuoteForm({...quoteForm, quoteNumber:event.target.value})}/></label>
              <label>통화<input required pattern="[A-Za-z]{3}" maxLength="3" value={quoteForm.currency} onChange={event => setQuoteForm({...quoteForm, currency:event.target.value.toUpperCase()})}/></label>
            </div>
            <label>품목 설명<input required maxLength="500" value={quoteForm.description} onChange={event => setQuoteForm({...quoteForm, description:event.target.value})}/></label>
            <div className="form-grid four">
              <label>MOQ<input type="number" min="0.000001" step="any" value={quoteForm.minimumQuantity} onChange={event => setQuoteForm({...quoteForm, minimumQuantity:event.target.value})}/></label>
              <label>단위<input maxLength="30" value={quoteForm.quantityUnit} onChange={event => setQuoteForm({...quoteForm, quantityUnit:event.target.value})}/></label>
              <label>단가<input type="number" min="0" step="0.0001" value={quoteForm.unitPrice} onChange={event => setQuoteForm({...quoteForm, unitPrice:event.target.value})}/></label>
              <label>납기(일)<input type="number" min="0" step="1" value={quoteForm.leadTimeDays} onChange={event => setQuoteForm({...quoteForm, leadTimeDays:event.target.value})}/></label>
            </div>
            <label>원산지 국가코드<input pattern="[A-Za-z]{2}" maxLength="2" value={quoteForm.countryOfOrigin} onChange={event => setQuoteForm({...quoteForm, countryOfOrigin:event.target.value.toUpperCase()})}/></label>
            <button className="button primary" disabled={saving}>견적 저장</button>
          </form>}
      </section>
    </div>}
    <section className="panel"><div className="panel-header"><div><h2>견적 비교 원장</h2><p className="muted">통화와 MOQ를 함께 보고 단가가 없는 견적은 미확인으로 유지합니다.</p></div></div>
      {loading ? <LoadingState/> : quotes.length === 0 ? <EmptyState title="등록한 공급 견적이 없습니다." description="같은 상품에 여러 공급처 견적을 연결해 비교하세요."/> :
        <div className="table-scroll"><table className="quote-table"><thead><tr><th>상태</th><th>공급처</th><th>상품</th><th>MOQ</th><th>단가</th><th>납기</th></tr></thead><tbody>
          {quotes.flatMap(quote => quote.lines.map(line => <tr key={`${quote.quoteId}-${line.lineNumber}`}><td><StatusBadge value={quote.status}/><small>{quote.quoteNumber || "번호 미입력"}</small></td><td>{quote.supplierName}</td><td><strong>{line.productName}</strong><small>{line.description}</small></td><td>{line.minimumQuantity == null ? "미확인" : `${line.minimumQuantity} ${line.quantityUnit || ""}`}</td><td>{line.unitPrice == null ? "미확인" : `${quote.currency} ${line.unitPrice}`}</td><td>{line.leadTimeDays == null ? "미확인" : `${line.leadTimeDays}일`}</td></tr>))}
        </tbody></table></div>}
    </section>
  </>;
}
