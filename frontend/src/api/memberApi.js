import { apiRequest } from "./client";
const base = "/organizations/current/members";
export const listMembers = (page = 0) => apiRequest(`${base}?page=${page}&size=20`);
export const addMember = data => apiRequest(base, { method: "POST", body: JSON.stringify(data) });
export const changeMember = (userId, data) => apiRequest(`${base}/${encodeURIComponent(userId)}`, { method: "PATCH", body: JSON.stringify(data) });
