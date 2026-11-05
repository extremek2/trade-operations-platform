import { apiRequest } from "./client";

export function createOrganization(payload) {
  return apiRequest("/organizations", { method: "POST", body: JSON.stringify(payload) });
}
