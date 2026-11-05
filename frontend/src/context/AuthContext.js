import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import * as authApi from "../api/authApi";
import { applySession, getSessionVersion, subscribeSession, withSessionLock } from "../api/client";

const AuthContext = createContext(null);
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState("");
  const restore = useCallback(async () => {
    setLoading(true); setSessionError("");
    try { await authApi.restoreSession(); }
    catch { setSessionError("세션을 확인하지 못했습니다. 연결 상태를 확인한 후 다시 시도해 주세요."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => subscribeSession(data => { setUser(data?.user || null); setSessionError(""); }), []);
  useEffect(() => { restore(); }, [restore]);
  const authenticate = useCallback((action, payload) => withSessionLock(async () => {
    const data = await action(payload); applySession(data); return data;
  }), []);
  const refreshUser = useCallback(async () => {
    const version = getSessionVersion();
    const data = await authApi.me();
    if (getSessionVersion() === version) setUser(data);
    return data;
  }, []);
  const switchContext = useCallback(async payload => {
    // Refresh outside the mutation lock, so an expired access token cannot deadlock it.
    await authApi.restoreSession();
    return authenticate(authApi.switchContext, payload);
  }, [authenticate]);
  const logout = useCallback(() => withSessionLock(async () => {
    await authApi.logout();
    applySession(null);
  }), []);
  const value = useMemo(() => ({ user, loading, sessionError, restore, refreshUser, switchContext, logout,
    signup: p => authenticate(authApi.signup, p), login: p => authenticate(authApi.login, p),
    platformLogin: p => authenticate(authApi.platformLogin, p) }),
    [user, loading, sessionError, restore, refreshUser, switchContext, logout, authenticate]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { const context = useContext(AuthContext); if (!context) throw new Error("AuthProvider가 필요합니다."); return context; }
