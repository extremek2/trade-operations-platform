import { useCallback, useEffect, useRef, useState } from 'react';
import * as api from '../api/collaborationApi';
import { createBusinessPartner, getBusinessPartners } from '../api/partnerApi';
import { ErrorMessage, LoadingState } from './Feedback';

const typeLabel = type => type === 'FORWARDER' ? '포워더' : '관세사';
const stateLabel = state => ({ INVITED: '초대 대기', ACTIVE: '참여 중', REVOKED: '철회', EXPIRED: '만료' }[state] || state);
export default function CasePartners({ shipmentId, archived }) {
  const [data, setData] = useState([]); const [businessPartners, setBusinessPartners] = useState([]);
  const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false);
  const [newPartner, setNewPartner] = useState({ name: '', role: 'FORWARDER' }); const [partnerSelection, setPartnerSelection] = useState('');
  const [selected, setSelected] = useState(''); const [invite, setInvite] = useState({ name: '', email: '', accessLevel: 'VIEWER' });
  const [decision, setDecision] = useState(null); const [reason, setReason] = useState(''); const pending = useRef(false);
  const load = useCallback(async () => {
    setLoading(true);
    try { const [p,c] = await Promise.all([api.partners(shipmentId), getBusinessPartners()]); setData(p); setBusinessPartners(c); }
    catch (err) { setData([]); setError(err.message); }
    finally { setLoading(false); }
  }, [shipmentId]);
  useEffect(() => { load(); }, [load]);
  const run = async (action, message) => {
    if (pending.current) return; pending.current = true; setBusy(true); setError(''); setNotice('');
    try { await action(); setNotice(message); setDecision(null); setReason(''); setInvite({ name: '', email: '', accessLevel: 'VIEWER' }); await load(); }
    catch (err) { setError(err.message); setDecision(null); await load(); }
    finally { setBusy(false); pending.current = false; }
  };
  const attachOptions = businessPartners.flatMap(partner => partner.roles
    .filter(role => ['FORWARDER', 'CUSTOMS_BROKER'].includes(role))
    .map(role => ({ key: `${partner.businessPartnerId}:${role}`, businessPartnerId: partner.businessPartnerId, name: partner.name, role })))
    .filter(option => !data.some(partner => partner.businessPartnerId === option.businessPartnerId));
  return <section className="panel case-partners"><div className="panel-header"><div><h2>외부 업체와 담당자</h2><p className="muted">이 건에서 업체별 초대 대기·참여 중 담당자 합계 최대 3명입니다.</p></div><button className="button secondary" disabled={busy || loading} onClick={() => { setError(''); setDecision(null); load(); }}>참여자 새로고침</button></div>
    <div className="panel-body"><ErrorMessage message={error}/>{notice && <p role="status">{notice}</p>}
      {loading ? <LoadingState/> : <>{data.map(p => <article className="partner-card" key={p.casePartnerId}><h3>{p.name} · {typeLabel(p.role)}</h3><p>{p.occupied} / 3명</p>
        <ul className="partner-people">{p.participants.map(person => <li key={person.participantId}><div><strong>{person.name}</strong><small>{person.email}</small><span>{stateLabel(person.status)} · {person.accessLevel === 'CONTRIBUTOR' ? '조회 및 문서번호 등록' : '조회 전용'}</span></div>
          {['ACTIVE','INVITED'].includes(person.status) && <button className="button danger" disabled={busy} aria-label={`${person.email} 참여 철회`} onClick={() => { setDecision({ kind: 'revoke', person, partner: p }); setReason(''); }}>철회</button>}</li>)}</ul>
        {!archived && <button className="button secondary" disabled={busy || p.occupied >= 3} aria-label={`${p.name} 담당자 초대`} onClick={() => { setSelected(p.casePartnerId); setDecision(null); }}>담당자 초대</button>}
      </article>)}{!data.length && <p>연결된 외부 업체가 없습니다.</p>}</>}
      {decision && <div className="partner-confirm" role="dialog" aria-labelledby="partner-confirm-title"><h3 id="partner-confirm-title">{decision.kind === 'invite' ? '이메일 초대 확인' : '참여 철회 확인'}</h3>
        <p>{decision.partner.name} · {decision.kind === 'invite' ? invite.email : decision.person.email}</p>
        {decision.kind === 'invite' ? <p>{invite.accessLevel === 'CONTRIBUTOR' ? '이 건 조회 및 운송 문서번호 등록' : '이 건 조회 전용'} 권한으로 초대합니다.</p> : <><p>이 건의 접근 권한과 기존 접속 링크·세션을 해제합니다.</p><label>철회 사유<textarea required maxLength={500} value={reason} onChange={e => setReason(e.target.value)}/></label></>}
        <div className="form-actions"><button className="button primary" disabled={busy || (decision.kind === 'revoke' && !reason.trim())} onClick={() => decision.kind === 'invite'
          ? run(() => api.invite(shipmentId, decision.partner.casePartnerId, invite), '초대를 등록했습니다. 이메일 발송 대기열에 저장했습니다.')
          : run(() => api.revoke(shipmentId, decision.person.participantId, reason.trim()), '참여 권한을 철회했습니다.')}>최종 확인</button><button className="button secondary" disabled={busy} onClick={() => setDecision(null)}>취소</button></div>
      </div>}
      {!archived && !loading && !decision && <>
        {selected && data.some(p => p.casePartnerId === selected && p.occupied < 3) && <form className="form-stack partner-form" onSubmit={e => { e.preventDefault(); setDecision({ kind: 'invite', partner: data.find(p => p.casePartnerId === selected) }); }}><h3>{data.find(p => p.casePartnerId === selected)?.name} 담당자 초대</h3>
          <label>담당자 이름<input required maxLength={200} value={invite.name} onChange={e => setInvite({ ...invite, name: e.target.value })}/></label><label>담당자 이메일<input required type="email" maxLength={255} value={invite.email} onChange={e => setInvite({ ...invite, email: e.target.value })}/></label>
          <label>참여 권한<select value={invite.accessLevel} onChange={e => setInvite({ ...invite, accessLevel: e.target.value })}><option value="VIEWER">조회 전용</option><option value="CONTRIBUTOR">조회 및 운송 문서번호 등록</option></select></label><button className="button primary" disabled={busy}>초대 내용 확인</button>
        </form>}
        {attachOptions.length > 0 && <form className="form-stack partner-form" onSubmit={e => { e.preventDefault(); const selected = attachOptions.find(option => option.key === partnerSelection); run(() => api.attachBusinessPartner(shipmentId, selected.businessPartnerId, selected.role), '이 건에 업체를 연결했습니다.'); }}><h3>등록된 업체 연결</h3><label>외부 업체<select required value={partnerSelection} onChange={e => setPartnerSelection(e.target.value)}><option value="">업체 선택</option>{attachOptions.map(option => <option value={option.key} key={option.key}>{option.name} · {typeLabel(option.role)}</option>)}</select></label><button className="button secondary" disabled={busy || !attachOptions.some(option => option.key === partnerSelection)}>이 건에 업체 연결</button></form>}
        <form className="form-stack partner-form" onSubmit={e => { e.preventDefault(); run(async () => { const partner = await createBusinessPartner({ name: newPartner.name, roles: [newPartner.role] }); setNewPartner({ name: '', role: 'FORWARDER' }); setPartnerSelection(`${partner.businessPartnerId}:${newPartner.role}`); }, '업체를 등록했습니다. 아래 선택 목록에서 이 건에 연결해 주세요.'); }}><h3>새 외부 업체 등록</h3><label>업체명<input required maxLength={200} value={newPartner.name} onChange={e => setNewPartner({ ...newPartner, name: e.target.value })}/></label><label>업체 역할<select value={newPartner.role} onChange={e => setNewPartner({ ...newPartner, role: e.target.value })}><option value="FORWARDER">포워더</option><option value="CUSTOMS_BROKER">관세사</option></select></label><button className="button secondary" disabled={busy || !newPartner.name.trim()}>업체 등록</button></form>
      </>}
    </div>
  </section>;
}
