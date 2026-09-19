const API_URL = process.env.REACT_APP_API_URL || "http://localhost:8080/api/v1";
let accessToken = null;
let refreshPromise = null;
let sessionVersion = 0;
export const getSessionVersion = () => sessionVersion;
const listeners = new Set();

export function setAccessToken(token) { accessToken = token; }
export function subscribeSession(listener) { listeners.add(listener); return () => listeners.delete(listener); }
export function applySession(data) {
  sessionVersion++;
  setAccessToken(data?.accessToken || null);
  listeners.forEach(listener => listener(data));
}

export function withSessionLock(action) {
  return typeof navigator !== "undefined" && navigator.locks?.request
    ? navigator.locks.request("trade-ops-session", action)
    : action();
}

export class ApiError extends Error {
  constructor(message, status) { super(message); this.name = "ApiError"; this.status = status; }
}

const authenticationEndpoints = new Set(["/auth/signup", "/auth/login", "/auth/platform/login", "/auth/refresh", "/auth/logout"]);

export async function apiRequest(path, options = {}, retry = true) {
  const multipart = typeof FormData !== "undefined" && options.body instanceof FormData;
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: { ...(!multipart && { "Content-Type": "application/json" }), ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}), ...options.headers },
    credentials: "include",
  });
  if (response.status === 401 && retry && !authenticationEndpoints.has(path)) {
    const refreshed = await refreshAccessToken();
    if (refreshed) return apiRequest(path, options, false);
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) {
    if (response.status === 401 && !authenticationEndpoints.has(path)) applySession(null);
    throw new ApiError(payload?.message || "요청을 처리하지 못했습니다. 다시 시도해 주세요.", response.status);
  }
  return payload?.data;
}

export async function confirmEmail(token) {
  // Verification is independent of any other account logged in to this browser.
  const response = await fetch(`${API_URL}/auth/email-verifications/confirm`, {
    method: "POST", credentials: "omit", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token }),
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) throw new ApiError(payload?.message || "이메일을 확인하지 못했습니다. 다시 시도해 주세요.", response.status);
}

export function refreshAccessToken() {
  if (!refreshPromise) refreshPromise = withSessionLock(performRefresh).finally(() => { refreshPromise = null; });
  return refreshPromise;
}

async function performRefresh() {
  const response = await fetch(`${API_URL}/auth/refresh`, { method: "POST", credentials: "include", headers: { "Content-Type": "application/json" } });
  const payload = await response.json().catch(() => null);
  if (response.status === 401 || response.status === 403) { applySession(null); return null; }
  if (!response.ok || !payload?.data) throw new ApiError(payload?.message || "서버에 연결하지 못했습니다. 다시 시도해 주세요.", response.status);
  applySession(payload.data);
  return payload.data;
}
