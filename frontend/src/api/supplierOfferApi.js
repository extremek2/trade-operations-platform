import { apiRequest } from "./client";

export const getSupplierOfferDrafts = () => apiRequest("/supplier-offer-drafts");
export const createSupplierOfferDraft = payload => apiRequest("/supplier-offer-drafts", {
  method: "POST", body: JSON.stringify(payload),
});
export const reviewSupplierOfferLine = (draftId, lineNumber, payload) =>
  apiRequest(`/supplier-offer-drafts/${draftId}/lines/${lineNumber}`, {
    method: "PUT", body: JSON.stringify(payload),
  });
export const excludeSupplierOfferLine = (draftId, lineNumber, version) =>
  apiRequest(`/supplier-offer-drafts/${draftId}/lines/${lineNumber}/exclude`, {
    method: "POST", body: JSON.stringify({ version }),
  });
export const confirmSupplierOfferDraft = (draftId, version) =>
  apiRequest(`/supplier-offer-drafts/${draftId}/confirm`, {
    method: "POST", body: JSON.stringify({ version }),
  });
export const createPurchaseSelection = (draftId, payload) =>
  apiRequest(`/supplier-offer-drafts/${draftId}/selections`, {
    method: "POST", body: JSON.stringify(payload),
  });
export const getPurchaseSelections = () => apiRequest("/purchase-selections");
