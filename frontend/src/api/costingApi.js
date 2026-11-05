import { apiRequest } from "./client";

export function getCostScenarios() { return apiRequest("/cost-scenarios"); }
export function createCostScenario(payload) {
  return apiRequest("/cost-scenarios", { method: "POST", body: JSON.stringify(payload) });
}
