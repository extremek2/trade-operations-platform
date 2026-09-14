import { apiRequest } from "./client";

export const getTradeCycles = () => apiRequest("/trade-cycles/purchase-orders");
export const createPurchaseOrder = payload => apiRequest("/trade-cycles/purchase-orders", {
  method: "POST", body: JSON.stringify(payload),
});
export const approvePurchaseOrder = (orderId, version) => apiRequest(`/trade-cycles/purchase-orders/${orderId}/approve`, {
  method: "POST", body: JSON.stringify({ version }),
});
export const cancelPurchaseOrder = (orderId, version) => apiRequest(`/trade-cycles/purchase-orders/${orderId}/cancel`, {
  method: "POST", body: JSON.stringify({ version }),
});
export const recordPurchasePayment = (orderId, payload) => apiRequest(`/trade-cycles/purchase-orders/${orderId}/payment`, {
  method: "POST", body: JSON.stringify(payload),
});
export const allocateShipment = payload => apiRequest("/trade-cycles/shipment-allocations", {
  method: "POST", body: JSON.stringify(payload),
});
export const createReceipt = payload => apiRequest("/trade-cycles/receipts", {
  method: "POST", body: JSON.stringify(payload),
});
export const addActualCost = (orderId, payload) => apiRequest(`/trade-cycles/purchase-orders/${orderId}/actual-costs`, {
  method: "POST", body: JSON.stringify(payload),
});
export const closeActualCost = orderId => apiRequest(`/trade-cycles/purchase-orders/${orderId}/cost-close`, { method: "POST" });
export const addSalesObservation = (orderId, payload) => apiRequest(`/trade-cycles/purchase-orders/${orderId}/sales`, {
  method: "POST", body: JSON.stringify(payload),
});
export const addReorderDecision = (orderId, payload) => apiRequest(`/trade-cycles/purchase-orders/${orderId}/decisions`, {
  method: "POST", body: JSON.stringify(payload),
});
