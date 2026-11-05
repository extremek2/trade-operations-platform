import { apiRequest, ApiError, applySession, withSessionLock } from "./client";
const API_URL = process.env.REACT_APP_API_URL || "http://localhost:8080/api/v1";
export const partners = shipmentId => apiRequest(`/shipments/${shipmentId}/partners`);
export const attachBusinessPartner = (shipmentId, businessPartnerId, role) => apiRequest(`/shipments/${shipmentId}/partners`, { method: 'POST', body: JSON.stringify({ businessPartnerId, role }) });
export const invite = (shipmentId, partnerId, data) => apiRequest(`/shipments/${shipmentId}/partners/${partnerId}/invitations`, { method: 'POST', body: JSON.stringify(data) });
export const revoke = (shipmentId, participantId, reason) => apiRequest(`/shipments/${shipmentId}/participants/${participantId}/revoke`, { method: 'POST', body: JSON.stringify({ reason }) });
export const caseWorkspace = shipmentId => apiRequest(`/case-workspace/${shipmentId}`);
export const addCaseDocument = (shipmentId, data) => apiRequest(`/case-workspace/${shipmentId}/documents`, { method: 'POST', body: JSON.stringify({ ...data, primary: false }) });
async function linkRequest(action, data, credentials) {
  const response = await fetch(`${API_URL}/auth/case-links/${action}`, { method: 'POST', credentials,
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) throw new ApiError(payload?.message || '접속 요청을 처리하지 못했습니다.', response.status);
  return payload?.data;
}
export const requestCaseLink = data => linkRequest('request', data, 'omit');
export const confirmCaseLink = token => withSessionLock(async () => {
  const data = await linkRequest('confirm', { token }, 'include');
  applySession(data); return data;
});
