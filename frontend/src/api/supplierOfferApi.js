import { apiBlob, apiRequest } from "./client";

export const getSupplierOfferDrafts = () => apiRequest("/supplier-offer-drafts");
export const createSupplierOfferDraft = payload => apiRequest("/supplier-offer-drafts", {
  method: "POST", body: JSON.stringify(payload),
});
export const importSupplierOfferFile = ({ supplierId, currency, sourceReference, file }) => {
  const body = new FormData();
  body.append("supplierId", supplierId);
  body.append("currency", currency);
  if (sourceReference) body.append("sourceReference", sourceReference);
  body.append("file", file);
  return apiRequest("/supplier-offer-drafts/import", { method: "POST", body });
};
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
export const reviewSupplierOfferDraft = (draftId, payload) =>
  apiRequest(`/supplier-offer-drafts/${draftId}/review`, {
    method: "POST", body: JSON.stringify(payload),
  });
export const createPurchaseSelection = (draftId, payload) =>
  apiRequest(`/supplier-offer-drafts/${draftId}/selections`, {
    method: "POST", body: JSON.stringify(payload),
  });
export const getPurchaseSelections = () => apiRequest("/purchase-selections");
export const getSupplierDocuments = () => apiRequest("/supplier-documents");
export const importSupplierDocument = ({ supplierId, sourceReference, file }) => {
  const body = new FormData();
  body.append("supplierId", supplierId);
  if (sourceReference) body.append("sourceReference", sourceReference);
  body.append("file", file);
  return apiRequest("/supplier-documents/import", { method: "POST", body });
};
export const createDocumentDraft = (documentId, currency) =>
  apiRequest(`/supplier-documents/${documentId}/draft`, { method: "POST", body: JSON.stringify({ currency }) });
export const getSupplierDocumentContent = documentId => apiBlob(`/supplier-documents/${documentId}/content`);
