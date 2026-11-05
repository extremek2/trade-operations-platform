import { useCallback, useEffect, useRef, useState } from "react";
import { applicationDetail, decideApplication } from "../api/applicationApi";
import ApplicationStatus, { ApplicationSummary, formatDate } from "../components/ApplicationStatus";
import { ErrorMessage, LoadingState } from "../components/Feedback";

export default function AdminApplicationDetailPage({ applicationId, navigate }) {
  const [detail, setDetail] = useState(null); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const [notice, setNotice] = useState(""); const [reason, setReason] = useState(""); const [note, setNote] = useState("");
  const [confirmation, setConfirmation] = useState(null); const [busy, setBusy] = useState(false); const submitting = useRef(false); const dialog = useRef(null);
  const load = useCallback(async () => {
    setLoading(true); setConfirmation(null); setError("");
    try { setDetail(await applicationDetail(applicationId)); }
    catch (err) { setDetail(null); setError(err.message); }
    finally { setLoading(false); }
  }, [applicationId]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    if (confirmation && dialog.current) { dialog.current.focus(); }
  }, [confirmation]);
  const decide = async () => {
    if (submitting.current || !confirmation || !reason.trim()) return;
    submitting.current = true; setBusy(true); setError(""); setNotice("");
    try {
      const result = await decideApplication(applicationId, confirmation, { version: detail.application.version, reason: reason.trim(), internalNote: note.trim() || null });
      setDetail(result); setNotice(confirmation === "approve" ? "조직 개설을 승인했습니다." : "신청을 반려했습니다."); setConfirmation(null);
    } catch (err) {
      setConfirmation(null);
      if (err.status === 409) {
        setDetail(null);
        try { setDetail(await applicationDetail(applicationId)); setError("다른 처리 또는 변경이 확인되어 최신 신청을 불러왔습니다. 내용을 다시 검토해 주세요."); }
        catch { setError("신청이 변경되었습니다. 최신 정보를 다시 불러온 후 검토해 주세요."); }
      } else {
        // A lost response can follow a committed decision. Reload before offering another decision.
        setDetail(null); setError(`${err.message} 최신 처리 상태를 다시 불러온 후 확인해 주세요.`);
      }
    } finally { submitting.current = false; setBusy(false); }
  };
  const application = detail?.application;
  return <><button className="back" disabled={busy} onClick={() => navigate("/system-admin/applications")}>← 신청 목록으로</button>
    <header className="page-header"><div><span className="eyebrow">APPLICATION REVIEW</span><h1>조직 개설 신청 상세</h1></div><button className="button secondary" disabled={loading || busy} onClick={load}>최신 정보 불러오기</button></header>
    <ErrorMessage message={error}/>{notice && <div className="alert success" role="status">{notice}</div>}
    {loading ? <LoadingState/> : application && <>
      <section className="panel application-card"><div className="panel-header"><h2>{application.organizationName}</h2><ApplicationStatus value={application.status}/></div><ApplicationSummary application={application}/></section>
      {application.status === "PENDING_REVIEW" ? <section className="panel panel-body form-stack"><h2>검토 결과</h2><p className="muted">승인하면 이 신청자를 조직 소유자로 지정하고 화주 작업 공간을 생성합니다.</p>
        <label>신청자에게 전달할 사유<textarea required maxLength={2000} rows={3} disabled={busy} value={reason} onChange={e => setReason(e.target.value)}/></label>
        <label>관리자 내부 메모 (선택)<textarea maxLength={4000} rows={3} disabled={busy} value={note} onChange={e => setNote(e.target.value)}/></label><p className="muted">내부 메모는 신청자에게 공개되지 않습니다.</p>
        <div className="form-actions"><button className="button danger" disabled={busy || !reason.trim()} onClick={() => setConfirmation("reject")}>반려 검토</button><button className="button primary" disabled={busy || !reason.trim()} onClick={() => setConfirmation("approve")}>승인 검토</button></div>
        {confirmation && <section className="decision-confirmation" role="dialog" aria-modal="false" aria-labelledby="decision-title" tabIndex={-1} ref={dialog} onKeyDown={event => { if (event.key === "Escape" && !busy) setConfirmation(null); }}>
          <h2 id="decision-title">{confirmation === "approve" ? "조직 개설 승인 확인" : "신청 반려 확인"}</h2><p><strong>{application.applicantName} · {application.applicantEmail}</strong></p>
          <p>{confirmation === "approve" ? `${application.organizationName}의 조직 소유자 권한을 부여합니다.` : "신청자에게 아래 보완 사유를 전달합니다."}</p><p className="preserve-lines">{reason}</p>
          <div className="form-actions"><button className="button ghost" disabled={busy} onClick={() => setConfirmation(null)}>취소</button><button className={`button ${confirmation === "approve" ? "primary" : "danger"}`} disabled={busy} onClick={decide}>{busy ? "처리 중…" : confirmation === "approve" ? "최종 승인" : "최종 반려"}</button></div>
        </section>}
      </section> : application.status === "PENDING_EMAIL" ? <div className="alert info">신청자가 이메일 확인을 완료해야 승인 또는 반려할 수 있습니다.</div> : <section className="panel panel-body"><h2>처리 이력</h2><p className="muted">{formatDate(application.reviewedAt)}</p><h3>신청자에게 전달한 사유</h3><p className="preserve-lines">{application.reason}</p><h3>관리자 내부 메모</h3><p className="preserve-lines">{detail.internalNote || "등록된 메모가 없습니다."}</p></section>}
    </>}
  </>;
}
