import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import * as api from "../api/applicationApi";
import { ErrorMessage, LoadingState } from "../components/Feedback";
import ApplicationStatus, { ApplicationSummary, formatDate } from "../components/ApplicationStatus";
import Pagination from "../components/Pagination";

function ApplicationForm({ previous, onSubmitted }) {
  const [form, setForm] = useState({ organizationName: previous?.organizationName || "", businessNumber: previous?.businessNumber || "", phone: previous?.phone || "" });
  const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const lock = useRef(false);
  const submit = async event => {
    event.preventDefault(); if (lock.current) return; lock.current = true; setBusy(true); setError("");
    try { await api.submitApplication({ ...form, previousApplicationId: previous?.applicationId || null }); await onSubmitted(); }
    catch (err) { setError(err.message); }
    finally { lock.current = false; setBusy(false); }
  };
  return <form className="panel panel-body form-stack" onSubmit={submit}><h2>{previous ? "내용을 보완해 다시 신청" : "조직 개설 신청"}</h2>
    <p className="muted">{previous ? "이전 신청과 반려 사유는 보존됩니다." : "회사 정보를 입력해 조직 개설을 신청해 주세요."}</p><ErrorMessage message={error}/>
    <label>회사명<input required maxLength={200} value={form.organizationName} onChange={e => setForm({ ...form, organizationName: e.target.value })}/></label>
    <label>사업자번호 (선택)<input maxLength={100} value={form.businessNumber} onChange={e => setForm({ ...form, businessNumber: e.target.value })}/></label>
    <label>연락처 (선택)<input maxLength={100} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })}/></label>
    <button className="button primary" disabled={busy}>{busy ? "신청 중…" : previous ? "보완 내용으로 재신청" : "조직 개설 신청"}</button>
  </form>;
}

export default function ApplicationPage({ navigate }) {
  const { user, refreshUser, switchContext } = useAuth();
  const [latest, setLatest] = useState(null); const [history, setHistory] = useState(null); const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false); const [cooldown, setCooldown] = useState(0); const requestId = useRef(0);
  const load = useCallback(async () => {
    const id = ++requestId.current; setLoading(true); setError("");
    try {
      const [current, past] = await Promise.all([api.myApplications(0, 1), api.myApplications(page, 10), refreshUser()]);
      if (id !== requestId.current) return;
      setLatest(current.content[0] || null); setHistory(past);
    } catch (err) { if (id === requestId.current) setError(err.message); }
    finally { if (id === requestId.current) setLoading(false); }
  }, [page, refreshUser]);
  const invalidate = useCallback(() => { requestId.current++; }, []);
  useEffect(() => { load(); return invalidate; }, [load, invalidate]);
  useEffect(() => { if (!cooldown) return; const timer = setTimeout(() => setCooldown(value => value - 1), 1000); return () => clearTimeout(timer); }, [cooldown]);
  const resend = async () => {
    setBusy(true); setError(""); setNotice("");
    try { await api.requestVerification(); setCooldown(60); setNotice("확인 메일 발송을 요청했습니다. 받은편지함과 스팸함을 확인해 주세요."); }
    catch (err) { setError(err.message); if (err.status === 429) setCooldown(60); }
    finally { setBusy(false); }
  };
  const enter = async () => {
    setBusy(true); setError("");
    try { await switchContext({ kind: "ORGANIZATION", organizationId: latest.approvedOrganizationId }); navigate("/", true); }
    catch (err) { setError(err.message); setBusy(false); }
  };
  return <><header className="page-header"><div><span className="eyebrow">ORGANIZATION APPLICATION</span><h1>조직 개설 신청</h1><p>이메일 확인과 관리자 검토가 완료되면 작업 공간을 이용할 수 있습니다.</p></div>
    <button className="button secondary" disabled={loading || busy} onClick={load}>상태 새로고침</button></header>
    <ErrorMessage message={error}/>{notice && <div className="alert success" role="status">{notice}</div>}
    {loading ? <LoadingState/> : <>
      {!history && <button className="button secondary" onClick={load}>다시 불러오기</button>}
      {history && <>
        {(!user.emailVerified && (!latest || latest.status === "PENDING_EMAIL")) && <section className="panel panel-body verification-callout"><h2>이메일 확인이 필요합니다</h2>
          <p><strong>{user.email}</strong>로 받은 링크를 열어 이메일 확인을 완료해 주세요.</p><p className="muted">링크는 15분 동안 유효합니다. 확인 후 상태를 새로고침해 주세요.</p>
          <button className="button primary" disabled={busy || cooldown > 0} onClick={resend}>{cooldown > 0 ? `${cooldown}초 후 재요청 가능` : "확인 메일 다시 요청"}</button></section>}
        {latest && <section className="panel application-card"><div className="panel-header"><h2>현재 신청</h2><ApplicationStatus value={latest.status}/></div>
          <ApplicationSummary application={latest}/><div className="panel-body status-explanation">
            {latest.status === "PENDING_REVIEW" && <p>이메일 확인이 완료되었습니다. 관리자가 회사 정보를 검토하고 있습니다.</p>}
            {latest.status === "APPROVED" && <><p>조직 개설이 승인되었습니다. 화주 작업 공간으로 이동할 수 있습니다.</p><button className="button primary" disabled={busy || !latest.approvedOrganizationId} onClick={enter}>{busy ? "이동 중…" : "작업 공간으로 이동"}</button></>}
            {latest.status === "REJECTED" && <><h2>보완이 필요한 내용</h2><p className="preserve-lines">{latest.reason}</p><p className="muted">아래 내용을 보완해 새 신청을 제출해 주세요.</p></>}
          </div></section>}
        {(!latest || latest.status === "REJECTED") && <ApplicationForm key={latest?.applicationId || "new"} previous={latest} onSubmitted={load}/>}
        <section className="panel application-history"><div className="panel-header"><h2>신청 이력</h2></div>
          {history.content.length ? <ul className="history-list">{history.content.map(item => <li key={item.applicationId}><div><strong>{item.organizationName}</strong><small>{formatDate(item.submittedAt)}</small></div><ApplicationStatus value={item.status}/>{item.reason && <p className="preserve-lines">{item.reason}</p>}</li>)}</ul> : <p className="panel-body muted">아직 제출한 신청이 없습니다.</p>}
          <Pagination page={page} totalPages={history.totalPages} onChange={setPage} disabled={loading}/></section>
      </>}
    </>}
  </>;
}
