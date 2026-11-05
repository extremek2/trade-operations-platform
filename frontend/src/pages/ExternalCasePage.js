import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { caseWorkspace, addCaseDocument } from '../api/collaborationApi';
import { ErrorMessage, LoadingState } from '../components/Feedback';

export default function ExternalCasePage() {
  const { user } = useAuth();
  const [data, setData] = useState(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false); const pending = useRef(false);
  const [document, setDocument] = useState({ documentType: 'MBL', documentNumber: '', issuerName: '' });
  const load = useCallback(async () => {
    setLoading(true); setData(null);
    try { setData(await caseWorkspace(user.shipmentId)); } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, [user.shipmentId]);
  useEffect(() => { load(); }, [load]);
  const submit = async e => {
    e.preventDefault(); if (pending.current) return; pending.current = true; setBusy(true); setError('');
    try { await addCaseDocument(user.shipmentId, document); setDocument({ ...document, documentNumber: '' }); await load(); }
    catch (err) { setError(err.message); }
    finally { pending.current = false; setBusy(false); }
  };
  return <><header className="page-header"><div><span className="eyebrow">EXTERNAL PARTICIPANT</span><h1>{data?.caseNumber || '참여한 건'}</h1><p>이메일로 초대받은 건의 운송 정보와 문서번호를 확인합니다.</p></div><button className="button secondary" disabled={busy || loading} onClick={() => { setError(''); load(); }}>건 새로고침</button></header>
    <ErrorMessage message={error}/>{loading ? <LoadingState/> : data && <>
      <section className="panel"><div className="panel-header"><h2>운송 정보</h2><span>{data.accessLevel === 'CONTRIBUTOR' ? '조회 및 문서번호 등록' : '조회 전용'}</span></div><dl className="detail-list"><div><dt>구간</dt><dd>{data.origin || '미정'} → {data.destination || '미정'}</dd></div><div><dt>운송 방식</dt><dd>{data.transportMode}</dd></div><div><dt>운송사</dt><dd>{data.carrierName || '미정'}</dd></div><div><dt>ETA</dt><dd>{data.eta ? new Date(data.eta).toLocaleString('ko-KR') : '미정'}</dd></div><div><dt>화물</dt><dd>{data.cargoDescription || '미입력'}</dd></div></dl></section>
      <section className="panel member-editor"><div className="panel-header"><h2>운송 문서번호</h2></div><div className="panel-body">{data.documents.length ? <ul className="document-list">{data.documents.map(d => <li key={d.documentId}>{d.documentType} · {d.documentNumber}</li>)}</ul> : <p>등록된 문서번호가 없습니다.</p>}
        {data.accessLevel === 'CONTRIBUTOR' && <form className="form-stack" onSubmit={submit}><label>문서 유형<select value={document.documentType} onChange={e => setDocument({ ...document, documentType: e.target.value })}>{['MBL','HBL','MAWB','HAWB','BOOKING','OTHER'].map(v => <option key={v}>{v}</option>)}</select></label>
          <label>문서번호<input required maxLength={200} value={document.documentNumber} onChange={e => setDocument({ ...document, documentNumber: e.target.value })}/></label><label>발행자<input maxLength={200} value={document.issuerName} onChange={e => setDocument({ ...document, issuerName: e.target.value })}/></label><button className="button primary" disabled={busy}>문서번호 등록</button></form>}
      </div></section>
    </>}
  </>;
}
