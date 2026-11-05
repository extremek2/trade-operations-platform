import { apiRequest, refreshAccessToken } from "./client";
export const signup = payload => apiRequest("/auth/signup", { method: "POST", body: JSON.stringify(payload) }, false);
export const login = payload => apiRequest("/auth/login", { method: "POST", body: JSON.stringify(payload) }, false);
export const platformLogin = payload => apiRequest("/auth/platform/login", { method: "POST", body: JSON.stringify(payload) }, false);
export const switchContext = payload => apiRequest("/auth/context", { method: "POST", body: JSON.stringify(payload) }, false);
export const me = () => apiRequest("/auth/me");
export const restoreSession = () => refreshAccessToken();
export const logout = () => apiRequest("/auth/logout", { method: "POST" }, false);
