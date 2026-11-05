import { useState } from "react";
import { createShipment } from "../api/shipmentApi";
import { ErrorMessage } from "../components/Feedback";

const initial = { caseNumber: "", direction: "IMPORT", transportMode: "SEA", currentStage: "PREPARATION", priority: "NORMAL", carrierName: "", vesselName: "", voyageNumber: "", flightNumber: "", originLocationCode: "", originLocationName: "", destinationLocationCode: "", destinationLocationName: "", etd: "", eta: "", cargoDescription: "", packageCount: "", grossWeight: "", weightUnit: "KG", containerCount: "" };
const nullableNumber = (value) => value === "" ? null : Number(value);
const nullableDate = (value) => value || null;

export default function ShipmentCreatePage({ navigate }) {
  const [form, setForm] = useState(initial); const [error, setError] = useState(""); const [submitting, setSubmitting] = useState(false);
  const change = e => setForm({ ...form, [e.target.name]: e.target.value });
  const submit = async e => { e.preventDefault(); setSubmitting(true); setError("");
    try { const result = await createShipment({ ...form,
      etd: nullableDate(form.etd), eta: nullableDate(form.eta), packageCount: nullableNumber(form.packageCount), grossWeight: nullableNumber(form.grossWeight), containerCount: nullableNumber(form.containerCount) }); navigate(`/shipments/${result.shipmentId}`); }
    catch (err) { setError(err.message); } finally { setSubmitting(false); }
  };
  return <><header className="page-header"><div><button className="back" onClick={() => navigate("/")}>← 현황으로</button><h1>화물 등록</h1><p>B/L이 발행되기 전이라도 내부 관리번호로 업무를 시작할 수 있습니다.</p></div></header>
    <form className="panel form-panel" onSubmit={submit}><ErrorMessage message={error}/>
      <section><h2>기본 정보</h2><div className="form-grid three"><label>관리번호<input name="caseNumber" required value={form.caseNumber} onChange={change} placeholder="IMP-2026-001"/></label><label>구분<select name="direction" value={form.direction} onChange={change}><option value="IMPORT">수입</option><option value="EXPORT">수출</option></select></label><label>운송 방식<select name="transportMode" value={form.transportMode} onChange={change}>{["SEA","AIR","ROAD","RAIL","MULTIMODAL"].map(v=><option key={v}>{v}</option>)}</select></label></div>
      <div className="form-grid two"><label>현재 단계<select name="currentStage" value={form.currentStage} onChange={change}>{["PREPARATION","BOOKING","DEPARTED","IN_TRANSIT","ARRIVED","CUSTOMS","DELIVERY","COMPLETED"].map(v=><option key={v}>{v}</option>)}</select></label><label>중요도<select name="priority" value={form.priority} onChange={change}><option value="NORMAL">정상</option><option value="ATTENTION">확인 필요</option><option value="URGENT">즉시 조치</option></select></label></div></section>
      <section><h2>운송 정보</h2><div className="form-grid two"><label>운송사<input name="carrierName" value={form.carrierName} onChange={change}/></label><label>선박명<input name="vesselName" value={form.vesselName} onChange={change}/></label><label>항차<input name="voyageNumber" value={form.voyageNumber} onChange={change}/></label><label>항공편<input name="flightNumber" value={form.flightNumber} onChange={change}/></label></div>
      <div className="form-grid two"><label>출발지 코드<input name="originLocationCode" value={form.originLocationCode} onChange={change} placeholder="CNSHA"/></label><label>출발지명<input name="originLocationName" value={form.originLocationName} onChange={change}/></label><label>도착지 코드<input name="destinationLocationCode" value={form.destinationLocationCode} onChange={change} placeholder="KRPUS"/></label><label>도착지명<input name="destinationLocationName" value={form.destinationLocationName} onChange={change}/></label><label>ETD<input type="datetime-local" name="etd" value={form.etd} onChange={change}/></label><label>ETA<input type="datetime-local" name="eta" value={form.eta} onChange={change}/></label></div></section>
      <section><h2>화물 정보</h2><label>화물 설명<textarea name="cargoDescription" value={form.cargoDescription} onChange={change}/></label><div className="form-grid four"><label>포장 수<input type="number" min="0" name="packageCount" value={form.packageCount} onChange={change}/></label><label>중량<input type="number" min="0" step="0.01" name="grossWeight" value={form.grossWeight} onChange={change}/></label><label>중량 단위<input name="weightUnit" value={form.weightUnit} onChange={change}/></label><label>컨테이너 수<input type="number" min="0" name="containerCount" value={form.containerCount} onChange={change}/></label></div></section>
      <div className="form-actions"><button type="button" className="button ghost" onClick={() => navigate("/")}>취소</button><button className="button primary" disabled={submitting}>{submitting ? "등록 중…" : "화물 등록"}</button></div></form></>;
}
