import { apiRequest } from "./client";

export const getBusinessPartners = () => apiRequest("/organizations/current/partners");
export const createBusinessPartner = data => apiRequest("/organizations/current/partners", {
  method: "POST", body: JSON.stringify(data),
});
