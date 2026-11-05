import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { listMembers, addMember, changeMember } from "../api/memberApi";
import { ErrorMessage, LoadingState } from "../components/Feedback";
import Pagination from "../components/Pagination";

export default function OrganizationMembersPage() {
  const { user, refreshUser } = useAuth();
  const [data, setData] = useState(null); const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false);
  const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  const [editing, setEditing] = useState(null); const [confirming, setConfirming] = useState(false);
  const [email, setEmail] = useState(""); const [role, setRole] = useState("OPERATOR");
  const [status, setStatus] = useState("ACTIVE"); const [reason, setReason] = useState("");
  const sequence = useRef(0); const submitting = useRef(false);
  const roles = user.role === "OWNER" ? ["ADMIN", "OPERATOR", "VIEWER"] : ["OPERATOR", "VIEWER"];
  const load = useCallback(async () => {
    const id = ++sequence.current; setLoading(true); setData(null);
    try {
      const result = await listMembers(page);
      if (id === sequence.current) setData(result);
    } catch (err) {
      if (id === sequence.current) setError(err.message);
      if (err.status === 403) await refreshUser().catch(() => {});
    } finally { if (id === sequence.current) setLoading(false); }
  }, [page, refreshUser]);
  const invalidate = useCallback(() => { sequence.current++; }, []);
  useEffect(() => { load(); return invalidate; }, [load, invalidate]);
  const close = () => { setEditing(null); setConfirming(false); setEmail(""); setRole("OPERATOR"); setStatus("ACTIVE"); setReason(""); };
  const select = member => {
    setEditing(member); setConfirming(false); setEmail(member.email); setRole(member.role);
    setStatus(member.status); setReason(""); setError(""); setNotice("");
  };
  const save = async () => {
    if (submitting.current) return;
    submitting.current = true; setBusy(true); setError(""); setNotice("");
    try {
      if (editing) await changeMember(editing.userId, { role, status, reason: reason.trim(), version: editing.version });
      else await addMember({ email: email.trim(), role, reason: reason.trim() });
      setNotice(editing ? "직원 권한과 상태를 변경했습니다." : "직원을 추가했습니다. 해당 직원은 다시 로그인하면 작업 공간에 진입할 수 있습니다.");
      close();
    } catch (err) {
      setError(err.message); close();
      if (err.status === 403) await refreshUser().catch(() => {});
    } finally {
      await load(); setBusy(false); submitting.current = false;
    }
  };
  const canChange = member => member.userId !== user.userId && member.role !== "OWNER" && (user.role === "OWNER" || member.role !== "ADMIN");
  return <>
    <header className="page-header"><div><span className="eyebrow">ORGANIZATION</span><h1>조직 직원 관리</h1><p>OWNER는 ADMIN을 지정·해제할 수 있고, ADMIN은 OPERATOR·VIEWER만 관리할 수 있습니다.</p></div>
      <button className="button secondary" disabled={loading || busy} onClick={() => { close(); setError(""); load(); }}>목록 새로고침</button></header>
    <ErrorMessage message={error}/>{notice && <p role="status" className="success-message">{notice}</p>}
    <section className="panel"><div className="panel-header"><h2>직원 목록</h2></div>
      {loading ? <LoadingState/> : data && <><div className="table-scroll"><table className="member-table"><thead><tr><th>직원</th><th>권한</th><th>상태</th><th>관리</th></tr></thead><tbody>{data.content.map(member => <tr key={member.userId}>
        <td className="member-person">{member.name}<small>{member.email}</small></td><td data-label="권한">{member.role}</td><td data-label="상태">{member.status === "ACTIVE" ? "활성" : "비활성"}</td><td className="member-actions" data-label="관리">{canChange(member) ? <button className="button secondary" disabled={busy} aria-label={`${member.email} 권한 변경`} onClick={() => select(member)}>권한 변경</button> : "변경 불가"}</td>
      </tr>)}</tbody></table></div><Pagination page={page} totalPages={data.totalPages} disabled={busy} onChange={next => { close(); setPage(next); }}/></>}
    </section>
    {data && !loading && <section className="panel setup-form member-editor"><h2>{editing ? "직원 권한 변경" : "내부 직원 추가"}</h2>
      <p className="muted">가입과 이메일 확인이 완료되고 다른 조직의 활성 소속이 없는 계정을 추가합니다. 포워더·관세사는 건별 외부 초대 대상입니다.</p>
      {confirming ? <div role="dialog" aria-labelledby="member-confirm-title"><h3 id="member-confirm-title">{editing ? "직원 변경 확인" : "직원 추가 확인"}</h3>
        <p className="member-email">{email}</p><p>{role} · {status === "ACTIVE" ? "활성" : "비활성"}</p><p>{reason}</p>
        {status === "INACTIVE" && <p>이 조직에 대한 접근과 기존 로그인 세션이 해제됩니다.</p>}
        <div className="form-actions"><button className="button primary" disabled={busy} onClick={save}>{busy ? "처리 중…" : "최종 확인"}</button><button className="button secondary" disabled={busy} onClick={() => setConfirming(false)}>돌아가기</button></div>
      </div> : <form onSubmit={e => { e.preventDefault(); setConfirming(true); }}>
        <label>직원 이메일<input type="email" maxLength={255} required disabled={!!editing || busy} value={email} onChange={e => setEmail(e.target.value)}/></label>
        <label>직원 권한<select value={role} disabled={busy} onChange={e => setRole(e.target.value)}>{roles.map(r => <option key={r}>{r}</option>)}</select></label>
        {editing && <label>직원 상태<select value={status} disabled={busy} onChange={e => setStatus(e.target.value)}><option value="ACTIVE">활성</option><option value="INACTIVE">비활성</option></select></label>}
        <label>변경 사유<textarea required maxLength={500} value={reason} disabled={busy} onChange={e => setReason(e.target.value)}/></label>
        <div className="form-actions"><button className="button primary" disabled={busy || !reason.trim() || (editing && editing.role === role && editing.status === status)}>내용 확인</button>{editing && <button className="button secondary" type="button" disabled={busy} onClick={close}>변경 취소</button>}</div>
      </form>}
    </section>}
  </>;
}
