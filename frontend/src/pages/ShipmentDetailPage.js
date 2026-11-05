import { useCallback, useEffect, useState } from "react";
import { addTransportDocument, archiveShipment, getShipment, updateShipment } from "../api/shipmentApi";
import { ErrorMessage, LoadingState } from "../components/Feedback";
import StatusBadge, { displayLabel } from "../components/StatusBadge";
import CasePartners from "../components/CasePartners";
import { useAuth } from "../context/AuthContext";

const date = value => value ? new Date(value).toLocaleString("ko-KR") : "미정";

export default function ShipmentDetailPage({ shipmentId, navigate }) {
  const { user } = useAuth();
  const [shipment, setShipment] = useState(null); const [error, setError] = useState(""); const [loading, setLoading] = useState(true);
  const [edit, setEdit] = useState({ currentStage: "", status: "", priority: "" });
  const [document, setDocument] = useState({ documentType: "MBL", documentNumber: "", issuerName: "", primary: true });
  const load = useCallback(async () => { setLoading(true); setError(""); try { const data = await getShipment(shipmentId); setShipment(data); setEdit({ currentStage: data.currentStage, status: data.status, priority: data.priority }); } catch (err) { setError(err.message); } finally { setLoading(false); } }, [shipmentId]);
  useEffect(() => { load(); }, [load]);
  const save = async () => { setError(""); try { const data = await updateShipment(shipmentId, { version: shipment.version, ...edit }); setShipment(data); } catch (err) { setError(err.message); } };
  const archive = async () => { if (!window.confirm("이 화물을 보관하시겠습니까?")) return; try { await archiveShipment(shipmentId); navigate("/"); } catch (err) { setError(err.message); } };
  const submitDocument = async e => { e.preventDefault(); setError(""); try { await addTransportDocument(shipmentId, document); setDocument({ ...document, documentNumber: "", issuerName: "" }); await load(); } catch (err) { setError(err.message); } };
  if (loading) return <LoadingState label="화물 상세 정보를 불러오는 중입니다."/>;
  if (!shipment) return <><ErrorMessage message={error}/><button className="button secondary" onClick={() => navigate("/")}>현황으로</button></>;
  return <><header className="page-header"><div><button className="back" onClick={() => navigate("/")}>← 현황으로</button><div className="title-row"><h1>{shipment.caseNumber}</h1><StatusBadge value={shipment.priority}/></div><p>{shipment.direction === "IMPORT" ? "수입" : "수출"} · {shipment.transportMode} · {shipment.carrierName || "운송사 미정"}</p></div>{!shipment.archivedAt && <button className="button danger" onClick={archive}>Archive</button>}</header>
    <ErrorMessage message={error}/>
    <div className="detail-grid"><section className="panel"><div className="panel-header"><div><h2>운송 개요</h2><p className="muted">현재 확인 가능한 일정과 구간</p></div></div><dl className="detail-list"><div><dt>구간</dt><dd>{shipment.originLocationCode || "—"} → {shipment.destinationLocationCode || "—"}</dd></div><div><dt>ETD</dt><dd>{date(shipment.etd)}</dd></div><div><dt>ETA</dt><dd>{date(shipment.eta)}</dd></div><div><dt>선박 / 항공편</dt><dd>{shipment.vesselName || shipment.flightNumber || "미정"}</dd></div><div><dt>화물</dt><dd>{shipment.cargoDescription || "입력 없음"}</dd></div><div><dt>중량</dt><dd>{shipment.grossWeight ? `${shipment.grossWeight} ${shipment.weightUnit || ""}` : "입력 없음"}</dd></div></dl></section>
      <section className="panel"><div className="panel-header"><div><h2>업무 상태</h2><p className="muted">version {shipment.version}</p></div></div><div className="panel-body form-stack"><label>현재 단계<select value={edit.currentStage} onChange={e=>setEdit({...edit,currentStage:e.target.value})}>{["PREPARATION","BOOKING","DEPARTED","IN_TRANSIT","ARRIVED","CUSTOMS","DELIVERY","COMPLETED"].map(v=><option key={v} value={v}>{displayLabel(v)}</option>)}</select></label><label>상태<select value={edit.status} onChange={e=>setEdit({...edit,status:e.target.value})}>{["OPEN","ON_HOLD","COMPLETED","CANCELLED"].map(v=><option key={v} value={v}>{displayLabel(v)}</option>)}</select></label><label>중요도<select value={edit.priority} onChange={e=>setEdit({...edit,priority:e.target.value})}>{["NORMAL","ATTENTION","URGENT"].map(v=><option key={v} value={v}>{displayLabel(v)}</option>)}</select></label><button className="button primary" onClick={save}>변경 저장</button></div></section></div>
    <section className="panel"><div className="panel-header"><div><h2>운송 문서</h2><p className="muted">B/L 또는 Booking 번호를 화물에 연결합니다.</p></div></div><div className="document-layout"><div>{shipment.documents.length === 0 ? <p className="muted document-empty">등록된 문서가 없습니다.</p> : <ul className="document-list">{shipment.documents.map(doc=><li key={doc.documentId}><StatusBadge value={doc.documentType}/><div><strong>{doc.documentNumber}</strong><small>{doc.issuerName || "발행자 미입력"}{doc.primary ? " · Primary" : ""}</small></div></li>)}</ul>}</div>
      {!shipment.archivedAt && <form className="document-form" onSubmit={submitDocument}><select value={document.documentType} onChange={e=>setDocument({...document,documentType:e.target.value})}>{["MBL","HBL","MAWB","HAWB","BOOKING","OTHER"].map(v=><option key={v}>{v}</option>)}</select><input required placeholder="문서번호" value={document.documentNumber} onChange={e=>setDocument({...document,documentNumber:e.target.value})}/><input placeholder="발행자" value={document.issuerName} onChange={e=>setDocument({...document,issuerName:e.target.value})}/><button className="button secondary">문서 추가</button></form>}</div></section>
    {["OWNER", "ADMIN"].includes(user.role) && <CasePartners key={shipmentId} shipmentId={shipmentId} archived={!!shipment.archivedAt}/>}
  </>;
}
