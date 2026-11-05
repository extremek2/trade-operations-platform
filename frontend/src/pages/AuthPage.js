import { useState } from "react";
import { useAuth } from "../context/AuthContext";
import { ErrorMessage } from "../components/Feedback";

export default function AuthPage({ navigate, platform = false }) {
  const { login, signup, platformLogin } = useAuth(); const [mode, setMode] = useState("login");
  const [form, setForm] = useState({ organizationName: "", businessNumber: "", name: "", email: "", password: "", phone: "" });
  const [error, setError] = useState(""); const [submitting, setSubmitting] = useState(false);
  const change = e => setForm({ ...form, [e.target.name]: e.target.value });
  const submit = async e => {
    e.preventDefault(); setSubmitting(true); setError("");
    try {
      const credentials = { email: form.email.trim(), password: form.password };
      const data = platform ? await platformLogin(credentials) : mode === "login" ? await login(credentials) : await signup({ ...form, email: form.email.trim() });
      const target = data.user.sessionKind === "PLATFORM" ? "/system-admin/applications" : data.user.sessionKind === "ACCOUNT" ? "/application" : "/";
      navigate(target, true);
    } catch (err) { setError(err.message); }
    finally { setSubmitting(false); }
  };
  return <div className="setup-page"><div className="setup-copy"><span className="eyebrow">{platform ? "SYSTEM ADMINISTRATION" : "SHIPPER WORKSPACE"}</span>
    <h1>{platform ? <>조직의 시작을<br/>확인하고 승인합니다.</> : <>무역 업무를<br/>한곳에서 연결합니다.</>}</h1>
    <p>{platform ? "조직 개설 신청과 검토 이력을 관리합니다." : "화주 중심으로 거래·선적·통관 업무를 관리합니다. 이메일 확인과 조직 개설 승인 후 작업 공간을 이용할 수 있습니다."}</p></div>
    <form className="panel setup-form" onSubmit={submit}>
      {platform ? <h2>시스템관리자 로그인</h2> : <div className="auth-tabs"><button type="button" disabled={submitting} className={mode === "login" ? "active" : ""} onClick={() => { setMode("login"); setError(""); }}>로그인</button><button type="button" disabled={submitting} className={mode === "signup" ? "active" : ""} onClick={() => { setMode("signup"); setError(""); }}>화주 회원가입</button></div>}
      <ErrorMessage message={error}/>
      {!platform && mode === "signup" && <><p className="muted">가입 후 이메일을 확인하면 관리자가 조직 개설 신청을 검토합니다.</p>
        <label>회사명<input name="organizationName" maxLength={200} value={form.organizationName} onChange={change} required/></label>
        <label>사업자번호 (선택)<input name="businessNumber" maxLength={100} value={form.businessNumber} onChange={change}/></label>
        <div className="form-grid two"><label>담당자명<input name="name" maxLength={200} value={form.name} onChange={change} required/></label><label>연락처 (선택)<input name="phone" maxLength={100} value={form.phone} onChange={change}/></label></div></>}
      <label>이메일<input type="email" name="email" autoComplete="username" value={form.email} onChange={change} required/></label>
      <label>비밀번호<input type="password" name="password" minLength={mode === "signup" ? 8 : undefined} maxLength={72} autoComplete={!platform && mode === "signup" ? "new-password" : "current-password"} value={form.password} onChange={change} required/></label>
      <button className="button primary wide" disabled={submitting}>{submitting ? "처리 중…" : platform ? "관리자 로그인" : mode === "login" ? "로그인" : "가입하고 조직 개설 신청"}</button>
      <button type="button" className="button ghost" disabled={submitting} onClick={() => navigate(platform ? "/" : "/system-admin/login")}>{platform ? "화주 로그인으로" : "시스템관리자 로그인"}</button>
    </form></div>;
}
