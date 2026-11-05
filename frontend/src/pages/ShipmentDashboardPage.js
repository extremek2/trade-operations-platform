import { useCallback, useEffect, useMemo, useState } from "react";
import { getShipments } from "../api/shipmentApi";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import StatusBadge, { displayLabel } from "../components/StatusBadge";

const formatDate = (value) => value ? new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "미정";

export default function ShipmentDashboardPage({ navigate }) {
  const [shipments, setShipments] = useState([]);
  const [filters, setFilters] = useState({ priority: "", stage: "", includeArchived: false });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    setLoading(true); setError("");
    try { setShipments(await getShipments(filters)); }
    catch (err) { setError(err.message); } finally { setLoading(false); }
  }, [filters]);
  useEffect(() => { load(); }, [load]);
  const counts = useMemo(() => ({
    total: shipments.length,
    urgent: shipments.filter((item) => item.priority === "URGENT").length,
    attention: shipments.filter((item) => item.priority === "ATTENTION").length,
    normal: shipments.filter((item) => item.priority === "NORMAL").length,
  }), [shipments]);
  const cards = [
    ["진행 화물", counts.total, "total"], ["즉시 조치", counts.urgent, "urgent"],
    ["확인 필요", counts.attention, "attention"], ["정상 진행", counts.normal, "normal"],
  ];
  return <>
    <header className="page-header"><div><span className="eyebrow">OPERATION DASHBOARD</span><h1>화물 현황</h1><p>문제가 있는 B/L과 다음 조치 대상을 먼저 확인합니다.</p></div>
      <button className="button primary" onClick={() => navigate("/shipments/new")}>+ 화물 등록</button></header>
    <section className="stats-grid">{cards.map(([label, value, type]) => <div className={`stat ${type}`} key={label}><span>{label}</span><strong>{loading ? "—" : value}</strong></div>)}</section>
    <section className="panel">
      <div className="panel-header"><div><h2>현재 업무</h2><p className="muted">ETA가 빠른 순으로 표시됩니다.</p></div>
        <div className="filters">
          <select aria-label="중요도" value={filters.priority} onChange={(e) => setFilters({ ...filters, priority: e.target.value })}><option value="">전체 중요도</option><option value="URGENT">즉시 조치</option><option value="ATTENTION">확인 필요</option><option value="NORMAL">정상</option></select>
          <select aria-label="단계" value={filters.stage} onChange={(e) => setFilters({ ...filters, stage: e.target.value })}><option value="">전체 단계</option>{["PREPARATION","BOOKING","DEPARTED","IN_TRANSIT","ARRIVED","CUSTOMS","DELIVERY","COMPLETED"].map(v => <option key={v} value={v}>{displayLabel(v)}</option>)}</select>
          <label className="check"><input type="checkbox" checked={filters.includeArchived} onChange={(e) => setFilters({ ...filters, includeArchived: e.target.checked })}/> 보관 포함</label>
        </div></div>
      <ErrorMessage message={error} />
      {loading ? <LoadingState /> : shipments.length === 0 ? <EmptyState title="표시할 화물이 없습니다." description="첫 B/L 업무 건을 등록해 대시보드를 확인해 보세요." action={<button className="button secondary" onClick={() => navigate("/shipments/new")}>화물 등록</button>} /> :
        <div className="table-scroll"><table><thead><tr><th>중요도</th><th>관리번호</th><th>구간</th><th>운송</th><th>현재 단계</th><th>ETA</th><th>상태</th></tr></thead>
          <tbody>{shipments.map(item => <tr key={item.shipmentId} onClick={() => navigate(`/shipments/${item.shipmentId}`)}><td><StatusBadge value={item.priority}/></td><td><strong>{item.caseNumber}</strong><small>{item.carrierName || "운송사 미정"}</small></td><td>{item.originLocationCode || "—"}<span className="route-arrow">→</span>{item.destinationLocationCode || "—"}</td><td>{item.transportMode}</td><td><StatusBadge value={item.currentStage}/></td><td>{formatDate(item.eta)}</td><td><StatusBadge value={item.status}/></td></tr>)}</tbody></table></div>}
    </section>
  </>;
}
