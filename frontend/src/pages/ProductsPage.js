import { useCallback, useEffect, useState } from "react";
import { changeProductStatus, createProduct, getProducts } from "../api/productApi";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import StatusBadge from "../components/StatusBadge";
import { useAuth } from "../context/AuthContext";

const initial = { name: "", hypothesis: "", internalSku: "", brand: "", category: "" };

export default function ProductsPage() {
  const { user } = useAuth();
  const editable = ["OWNER", "ADMIN", "OPERATOR"].includes(user?.role);
  const [products, setProducts] = useState([]);
  const [form, setForm] = useState(initial);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try { setProducts(await getProducts()); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const submit = async (event) => {
    event.preventDefault(); setSaving(true); setError("");
    try { await createProduct(form); setForm(initial); await load(); }
    catch (err) { setError(err.message); }
    finally { setSaving(false); }
  };

  const startSourcing = async (product) => {
    setError("");
    try { await changeProductStatus(product.productId, { status: "SOURCING", reason: "공급 조건 조사 시작", version: product.version }); await load(); }
    catch (err) { setError(err.message); }
  };

  return <>
    <header className="page-header"><div><span className="eyebrow">PRODUCT MASTER</span><h1>상품 후보</h1><p>발굴부터 판매 중단까지 같은 상품 원장을 사용합니다.</p></div></header>
    <ErrorMessage message={error}/>
    {editable && <section className="panel"><div className="panel-header"><div><h2>후보 등록</h2><p className="muted">확인되지 않은 값은 비워 두고 구매 가설부터 기록하세요.</p></div></div>
      <form className="setup-form" onSubmit={submit}>
        <label>상품명<input required maxLength="300" value={form.name} onChange={(e) => setForm({...form, name:e.target.value})}/></label>
        <label>구매 가설<textarea maxLength="2000" value={form.hypothesis} onChange={(e) => setForm({...form, hypothesis:e.target.value})}/></label>
        <div className="form-grid"><label>내부 SKU<input maxLength="100" value={form.internalSku} onChange={(e) => setForm({...form, internalSku:e.target.value})}/></label>
          <label>브랜드<input maxLength="200" value={form.brand} onChange={(e) => setForm({...form, brand:e.target.value})}/></label>
          <label>카테고리<input maxLength="200" value={form.category} onChange={(e) => setForm({...form, category:e.target.value})}/></label></div>
        <button className="button primary" disabled={saving}>{saving ? "저장 중" : "후보 등록"}</button>
      </form></section>}
    <section className="panel"><div className="panel-header"><div><h2>상품 원장</h2><p className="muted">후보를 승인할 때 새 상품으로 복사하지 않습니다.</p></div></div>
      {loading ? <LoadingState/> : products.length === 0 ? <EmptyState title="등록한 상품 후보가 없습니다." description="첫 후보와 구매 가설을 기록해 보세요."/> :
        <div className="table-scroll"><table><thead><tr><th>상태</th><th>상품</th><th>SKU</th><th>브랜드</th><th>다음 행동</th></tr></thead><tbody>
          {products.map(product => <tr key={product.productId}><td><StatusBadge value={product.status}/></td><td><strong>{product.name}</strong><small>{product.hypothesis || "구매 가설 미입력"}</small></td><td>{product.internalSku || "미정"}</td><td>{product.brand || "미정"}</td><td>{editable && product.status === "DISCOVERED" ? <button className="button secondary" onClick={() => startSourcing(product)}>공급 조사 시작</button> : "—"}</td></tr>)}
        </tbody></table></div>}
    </section>
  </>;
}
