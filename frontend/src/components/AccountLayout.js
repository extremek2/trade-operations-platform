import { useState } from "react";
import { useAuth } from "../context/AuthContext";
import { ErrorMessage } from "./Feedback";

export default function AccountLayout({ children, navigate, platform = false, external = false }) {
  const { user, logout } = useAuth();
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const leave = async () => {
    setBusy(true); setError("");
    try { await logout(); navigate(platform ? "/system-admin/login" : external ? "/external-access" : "/", true); }
    catch { setError("로그아웃하지 못했습니다. 다시 시도해 주세요."); }
    finally { setBusy(false); }
  };
  return <div className="account-shell">
    <header className="account-header"><button className="account-brand" onClick={() => navigate(platform ? "/system-admin/applications" : external ? "/external-case" : "/application")}>
      <span className="brand-mark">T</span> Trade Ops <small>{platform ? "시스템 관리" : external ? "건별 참여" : "조직 개설"}</small></button>
      <div className="account-user"><span>{user?.name}<small>{user?.email}</small></span><button className="button secondary" disabled={busy} onClick={leave}>로그아웃</button></div>
    </header><main className="account-content"><ErrorMessage message={error}/>{children}</main>
  </div>;
}
