import { apiRequest } from "./client";

export function getProducts() { return apiRequest("/products"); }
export function createProduct(payload) {
  return apiRequest("/products", { method: "POST", body: JSON.stringify(payload) });
}
export function changeProductStatus(productId, payload) {
  return apiRequest(`/products/${productId}/status`, { method: "POST", body: JSON.stringify(payload) });
}
