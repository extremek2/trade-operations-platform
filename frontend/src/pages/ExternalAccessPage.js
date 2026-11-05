import { useEffect, useRef, useState } from 'react';
import { confirmCaseLink, requestCaseLink } from '../api/collaborationApi';
import { ErrorMessage } from '../components/Feedback';

export default function ExternalAccessPage({ navigate }) {
  const [link, setLink] = useState(() => new URLSearchParams(window.location.hash.slice(1)));
  const [token, setToken] = useState(link.get('token') || '');
  const invitationId = link.get('invitation') || '';
  const [email, setEmail] = useState(''); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false); const [cooldown, setCooldown] = useState(0); const submitting = useRef(false);
  useEffect(() => {
    window.history.replaceState({}, '', window.location.pathname);
    const receiveLink = () => {
      const next = new URLSearchParams(window.location.hash.slice(1));
      setLink(next); setToken(next.get('token') || ''); setError(''); setNotice('');
      window.history.replaceState({}, '', window.location.pathname);
    };
    window.addEventListener('hashchange', receiveLink);
    return () => window.removeEventListener('hashchange', receiveLink);
  }, []);
  useEffect(() => { if (!cooldown) return undefined; const timer = setTimeout(() => setCooldown(cooldown - 1), 1000); return () => clearTimeout(timer); }, [cooldown]);
  const confirm = async () => {
    if (submitting.current) return; submitting.current = true; setBusy(true); setError('');
    try { await confirmCaseLink(token); setToken(''); navigate('/external-case', true); }
    catch (err) { setError(err.message); if ([400, 409].includes(err.status)) setToken(''); }
    finally { submitting.current = false; setBusy(false); }
  };
  const resend = async e => {
    e.preventDefault(); if (submitting.current || cooldown) return;
    submitting.current = true; setBusy(true); setError(''); setNotice('');
    try { await requestCaseLink({ invitationId, email: email.trim() }); setNotice('유효한 초대와 일치하는 이메일이면 새 접속 링크를 보냅니다. 받은 메일을 확인해 주세요.'); setCooldown(60); setToken(''); }
    catch (err) { setError(err.message); if (err.status === 429) setCooldown(60); }
    finally { submitting.current = false; setBusy(false); }
  };
  return <main className="verification-page"><section className="panel setup-form"><span className="eyebrow">CASE INVITATION</span><h1>건별 업무 참여</h1>
    <p>초대받은 이메일의 담당자로 이 건에 접속합니다. 화주 조직의 직원 권한은 부여되지 않습니다.</p>
    <ErrorMessage message={error}/>{notice && <p role="status">{notice}</p>}
    {/^[A-Za-z0-9_-]{43}$/.test(token) && <><p>확인하면 이 브라우저의 접속 계정이 초대받은 담당자로 전환됩니다.</p><button className="button primary" disabled={busy} onClick={confirm}>초대 확인하고 건에 접속</button></>}
    {/^[0-9a-f-]{36}$/i.test(invitationId) ? <form className="form-stack" onSubmit={resend}><h2>새 접속 링크 받기</h2><p className="muted">접속 링크가 만료되었거나 다시 접속할 때 초대받은 이메일을 입력해 주세요. 최초 수락 기한이 지나면 화주에게 재초대를 요청해 주세요.</p>
      <label>초대받은 이메일<input type="email" required maxLength={255} value={email} onChange={e => setEmail(e.target.value)}/></label><button className="button secondary" disabled={busy || cooldown > 0}>{cooldown ? `${cooldown}초 후 재요청 가능` : '접속 링크 요청'}</button>
    </form> : <p>받은 초대 메일의 링크를 다시 열어 주세요. 새로고침 전의 인증 정보는 저장하지 않습니다.</p>}
    <button className="button ghost" disabled={busy} onClick={() => navigate('/')}>화주 로그인으로</button>
  </section></main>;
}
