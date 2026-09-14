import { useCallback, useEffect, useState } from "react";
import AppLayout from "./components/AppLayout";
import AccountLayout from "./components/AccountLayout";
import { AuthProvider, useAuth } from "./context/AuthContext";
import AuthPage from "./pages/AuthPage";
import ApplicationPage from "./pages/ApplicationPage";
import EmailVerificationPage from "./pages/EmailVerificationPage";
import AdminApplicationsPage from "./pages/AdminApplicationsPage";
import AdminApplicationDetailPage from "./pages/AdminApplicationDetailPage";
import { ErrorMessage, LoadingState } from "./components/Feedback";
import ShipmentDashboardPage from "./pages/ShipmentDashboardPage";
import ShipmentCreatePage from "./pages/ShipmentCreatePage";
import ShipmentDetailPage from "./pages/ShipmentDetailPage";
import ProductsPage from "./pages/ProductsPage";
import SourcingPage from "./pages/SourcingPage";
import OrganizationMembersPage from "./pages/OrganizationMembersPage";
import ExternalAccessPage from "./pages/ExternalAccessPage";
import ExternalCasePage from "./pages/ExternalCasePage";
import "./styles/app.css";

function usePath() {
  const [path, setPath] = useState(window.location.pathname);
  useEffect(() => { const handler = () => setPath(window.location.pathname); window.addEventListener("popstate", handler); return () => window.removeEventListener("popstate", handler); }, []);
  const navigate = useCallback((next, replace = false) => {
    window.history[replace ? "replaceState" : "pushState"]({}, "", next); setPath(next); window.scrollTo(0, 0);
  }, []);
  return [path, navigate];
}
function Redirect({ to, navigate }) {
  useEffect(() => { navigate(to, true); }, [to, navigate]);
  return <LoadingState label="화면으로 이동하고 있습니다."/>;
}
function AccessDenied({ navigate, home }) {
  return <main className="verification-page"><section className="panel setup-form"><h1>접근 권한이 없습니다</h1><p>현재 계정으로 이용할 수 있는 화면으로 이동해 주세요.</p><button className="button primary" onClick={() => navigate(home, true)}>내 화면으로 이동</button><button className="button ghost" onClick={() => navigate("/system-admin/login")}>시스템관리자 로그인</button></section></main>;
}
function Routes() {
  const { user, loading, sessionError, restore } = useAuth(); const [path, navigate] = usePath();
  if (path === "/verify-email") return <EmailVerificationPage navigate={navigate}/>;
  if (path === "/external-access") return <ExternalAccessPage navigate={navigate}/>;
  if (loading) return <LoadingState label="세션을 확인하는 중입니다."/>;
  if (sessionError) return <main className="verification-page"><section className="panel setup-form"><h1>연결을 확인해 주세요</h1><ErrorMessage message={sessionError}/><button className="button primary" onClick={restore}>다시 연결</button></section></main>;
  if (path === "/system-admin/login") return user?.sessionKind === "PLATFORM"
    ? <Redirect to="/system-admin/applications" navigate={navigate}/>
    : <AuthPage key="platform-login" platform navigate={navigate}/>;
  if (!user && path === "/external-case") return <Redirect to="/external-access" navigate={navigate}/>;
  if (!user) return <AuthPage key={path.startsWith("/system-admin") ? "platform" : "shipper"} platform={path.startsWith("/system-admin")} navigate={navigate}/>;
  if (user.sessionKind === "CASE") {
    if (path !== "/external-case") return <Redirect to="/external-case" navigate={navigate}/>;
    return <AccountLayout external navigate={navigate}><ExternalCasePage key={`${user.userId}:${user.participantId}`}/></AccountLayout>;
  }
  if (user.sessionKind === "PLATFORM") {
    if (path === "/system-admin/applications") return <AccountLayout platform navigate={navigate}><AdminApplicationsPage key={user.userId} navigate={navigate}/></AccountLayout>;
    if (/^\/system-admin\/applications\/[^/]+$/.test(path)) return <AccountLayout platform navigate={navigate}><AdminApplicationDetailPage key={`${user.userId}:${path}`} applicationId={path.split("/")[3]} navigate={navigate}/></AccountLayout>;
    return <Redirect to="/system-admin/applications" navigate={navigate}/>;
  }
  const home = user.sessionKind === "ORGANIZATION" ? "/" : "/application";
  if (path.startsWith("/system-admin")) return <AccessDenied navigate={navigate} home={home}/>;
  if (path === "/application") return <AccountLayout navigate={navigate}><ApplicationPage key={user.userId} navigate={navigate}/></AccountLayout>;
  if (user.sessionKind === "ACCOUNT") return <Redirect to="/application" navigate={navigate}/>;
  if (user.sessionKind !== "ORGANIZATION") return <AccessDenied navigate={navigate} home="/application"/>;
  let page;
  if (path === "/organization/members") {
    if (!["OWNER", "ADMIN"].includes(user.role)) return <AccessDenied navigate={navigate} home="/"/>;
    page = <OrganizationMembersPage/>;
  }
  else if (path === "/products") page = <ProductsPage/>;
  else if (path === "/sourcing") page = <SourcingPage/>;
  else if (path === "/shipments/new") page = <ShipmentCreatePage navigate={navigate}/>;
  else if (/^\/shipments\/[^/]+$/.test(path)) page = <ShipmentDetailPage shipmentId={path.split("/")[2]} navigate={navigate}/>;
  else page = <ShipmentDashboardPage navigate={navigate}/>;
  return <AppLayout key={`${user.userId}:${user.organizationId}`} path={path} navigate={navigate}>{page}</AppLayout>;
}
export default function App() { return <AuthProvider><Routes/></AuthProvider>; }
