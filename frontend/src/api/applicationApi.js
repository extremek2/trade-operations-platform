import { apiRequest } from "./client";
export const myApplications = (page = 0, size = 10) => apiRequest(`/me/applications?page=${page}&size=${size}`);
export const submitApplication = data => apiRequest("/organization-applications", { method: "POST", body: JSON.stringify(data) });
export const requestVerification = () => apiRequest("/auth/email-verifications/request", { method: "POST" });
export const applicationQueue = (status = "PENDING_REVIEW", page = 0) => apiRequest(`/system-admin/applications?page=${page}&size=20${status ? `&status=${encodeURIComponent(status)}` : ""}`);
export const applicationDetail = id => apiRequest(`/system-admin/applications/${encodeURIComponent(id)}`);
export const decideApplication = (id, action, data) => apiRequest(`/system-admin/applications/${encodeURIComponent(id)}/${action}`, { method: "POST", body: JSON.stringify(data) });
