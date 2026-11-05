import { apiRequest } from "./client";

const query = (params) => new URLSearchParams(
  Object.entries(params).filter(([, value]) => value !== "" && value !== undefined && value !== null)
).toString();

export function getShipments(filters = {}) {
  return apiRequest(`/shipments?${query(filters)}`);
}

export function getShipment(shipmentId) {
  return apiRequest(`/shipments/${shipmentId}`);
}

export function createShipment(payload) {
  return apiRequest("/shipments", { method: "POST", body: JSON.stringify(payload) });
}

export function updateShipment(shipmentId, payload) {
  return apiRequest(`/shipments/${shipmentId}`, {
    method: "PATCH", body: JSON.stringify(payload),
  });
}

export function archiveShipment(shipmentId) {
  return apiRequest(`/shipments/${shipmentId}/archive`, { method: "POST" });
}

export function addTransportDocument(shipmentId, payload) {
  return apiRequest(`/shipments/${shipmentId}/documents`, {
    method: "POST", body: JSON.stringify(payload),
  });
}
