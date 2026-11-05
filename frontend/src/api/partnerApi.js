import { apiRequest } from "./client";

export const getBusinessPartners = () => apiRequest("/organizations/current/partners");
export const createBusinessPartner = data => apiRequest("/organizations/current/partners", {
  method: "POST", body: JSON.stringify(data),
});
export const updateSupplierInquiryContacts = (partnerId, data) =>
  apiRequest(`/organizations/current/partners/${partnerId}/inquiry-contacts`, {
    method: "PUT", body: JSON.stringify(data),
  });
