import { useCallback, useEffect, useRef, useState } from "react";
import { applicationQueue } from "../api/applicationApi";
import ApplicationStatus, { applicationLabels, formatDate } from "../components/ApplicationStatus";
import { EmptyState, ErrorMessage, LoadingState } from "../components/Feedback";
import Pagination from "../components/Pagination";

export default function AdminApplicationsPage({ navigate }) {
  const [status, setStatus] = useState("PENDING_REVIEW"); const [page, setPage] = useState(0);
  const [data, setData] = useState(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const requestId = useRef(0);
  const load = useCallback(async () => {
    const id = ++requestId.current; setLoading(true); setError("");
    try { const result = await applicationQueue(status, page); if (id === requestId.current) setData(result); }
    catch (err) { if (id === requestId.current) { setData(null); setError(err.message); } }
    finally { if (id === requestId.current) setLoading(false); }
  }, [status, page]);
  const invalidate = useCallback(() => { requestId.current++; }, []);
  useEffect(() => { load(); return invalidate; }, [load, invalidate]);
  return <><header className="page-header"><div><span className="eyebrow">SYSTEM ADMINISTRATION</span><h1>조직 개설 신청 검토</h1><p>신청 정보를 확인하고 최초 조직 소유자 권한을 승인합니다.</p></div><button className="button secondary" disabled={loading} onClick={load}>목록 새로고침</button></header>
    <section className="panel"><div className="panel-header"><h2>신청 목록</h2><label>신청 상태<select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">전체 상태</option>{Object.entries(applicationLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
      <div className="panel-body"><ErrorMessage message={error}/>{error && <button className="button secondary" onClick={load}>다시 불러오기</button>}</div>
      {loading ? <LoadingState/> : data?.content.length ? <><div className="table-scroll"><table><thead><tr><th>회사명</th><th>신청자</th><th>신청일</th><th>상태</th><th>검토</th></tr></thead><tbody>{data.content.map(item => <tr key={item.applicationId}>
        <td>{item.organizationName}<small>{item.businessNumber || "사업자번호 미입력"}</small></td><td>{item.applicantName}<small>{item.applicantEmail}</small></td><td>{formatDate(item.submittedAt)}</td><td><ApplicationStatus value={item.status}/></td>
        <td><button className="button secondary" aria-label={`${item.organizationName} 신청 상세 보기`} onClick={() => navigate(`/system-admin/applications/${item.applicationId}`)}>상세 보기</button></td></tr>)}</tbody></table></div>
        <Pagination page={page} totalPages={data.totalPages} onChange={setPage}/></> : data && <EmptyState title="해당 상태의 신청이 없습니다" description="다른 상태를 선택하거나 목록을 새로고침해 주세요."/>}
    </section></>;
}
