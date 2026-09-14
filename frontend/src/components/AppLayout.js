import { useState } from "react";
import { ErrorMessage } from "./Feedback";
import { useAuth } from "../context/AuthContext";

const navigation = [
  { path: "/", label: "화물 현황" },
  { path: "/shipments/new", label: "화물 등록" },
  { path: "/products", label: "상품 후보" },
  { path: "/sourcing", label: "공급 견적" },
  { path: "/costing", label: "예상 원가" },
  { path: "/trade-cycle", label: "수입 사이클" },
];

export default function AppLayout({ path, navigate, children }) {
  const { user, logout } = useAuth();
  const [logoutError, setLogoutError] = useState(""); const [leaving, setLeaving] = useState(false);
  const leave = async () => {
    setLeaving(true); setLogoutError("");
    try { await logout(); navigate("/", true); }
    catch { setLogoutError("로그아웃하지 못했습니다. 다시 시도해 주세요."); }
    finally { setLeaving(false); }
  };
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <button className="brand" onClick={() => navigate("/")}>
          <span className="brand-mark">T</span>
          <span>Trade Ops<small>B/L operation hub</small></span>
        </button>
        <nav>
          {navigation.map((item) => (
            <button key={item.path} className={`nav-link ${path === item.path ? "active" : ""}`}
                    onClick={() => navigate(item.path)}>{item.label}</button>
          ))}
          {["OWNER", "ADMIN"].includes(user?.role) && <button className={`nav-link ${path === "/organization/members" ? "active" : ""}`} onClick={() => navigate("/organization/members")}>조직 직원 관리</button>}
        </nav>
        {user && (
          <div className="workspace-card">
            <span>현재 조직</span>
            <strong>{user.organizationName}</strong>
            <small>{user.email} · {user.role}</small>
            <button disabled={leaving} onClick={leave}>로그아웃</button>
          </div>
        )}
      </aside>
      <main className="content"><ErrorMessage message={logoutError}/>{children}</main>
    </div>
  );
}
