import { useEffect, useRef, useState } from "react";
import { confirmEmail } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { ErrorMessage } from "../components/Feedback";

export default function EmailVerificationPage({ navigate }) {
  const { user, refreshUser } = useAuth();
  const [token, setToken] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get("token") || "");
  const [state, setState] = useState("ready"); const [error, setError] = useState(""); const submitting = useRef(false);
  const valid = /^[A-Za-z0-9_-]{43}$/.test(token);
  useEffect(() => {
    window.history.replaceState({}, "", window.location.pathname);
    const receiveLink = () => {
      setToken(new URLSearchParams(window.location.hash.slice(1)).get("token") || "");
      setState("ready"); setError(""); window.history.replaceState({}, "", window.location.pathname);
    };
    window.addEventListener("hashchange", receiveLink);
    return () => window.removeEventListener("hashchange", receiveLink);
  }, []);
  const confirm = async () => {
    if (submitting.current || !valid || state === "success") return;
    submitting.current = true; setState("submitting"); setError("");
    try {
      await confirmEmail(token); setToken(""); setState("success");
      if (user) { try { await refreshUser(); } catch { /* Verification has already succeeded; session recovery is separate. */ } }
    } catch (err) { setError(err.message); setState(err.status === 400 ? "invalid" : "ready"); }
    finally { submitting.current = false; }
  };
  return <main className="verification-page"><section className="panel setup-form"><span className="eyebrow">TRADE OPS</span><h1>이메일 주소 확인</h1><ErrorMessage message={error}/>
    {state === "success" ? <><div className="alert success" role="status">이메일 확인이 완료되었습니다.</div><p>신청한 계정으로 접속해 관리자 검토 상태를 확인해 주세요.</p></> : <>
      {valid && state !== "invalid" ? <><p>아래 버튼을 눌러 조직 개설 신청에 사용할 이메일을 확인해 주세요.</p><button className="button primary" disabled={state === "submitting"} onClick={confirm}>{state === "submitting" ? "확인 중…" : "이메일 확인 완료하기"}</button></> : <p>확인 링크가 없거나 사용할 수 없습니다. 신청 화면에서 새 확인 메일을 요청해 주세요.</p>}
      <p className="muted">이메일 확인 후 관리자가 조직 개설 신청을 검토합니다.</p>
    </>}
    <button className="button secondary" onClick={() => navigate(user ? "/application" : "/", true)}>{user ? "신청 상태 확인" : "로그인으로 이동"}</button>
  </section></main>;
}
