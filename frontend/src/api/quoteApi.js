import { apiRequest } from "./client";

export function getQuotes() { return apiRequest("/quotes"); }
export function createQuote(payload) {
  return apiRequest("/quotes", { method: "POST", body: JSON.stringify(payload) });
}
export function changeQuoteStatus(quoteId, payload) {
  return apiRequest(`/quotes/${quoteId}/status`, { method: "POST", body: JSON.stringify(payload) });
}
