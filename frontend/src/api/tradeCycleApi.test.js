import { apiRequest } from "./client";
import {
  addActualCost, addReorderDecision, addSalesObservation, allocateShipment, approvePurchaseOrder,
  cancelPurchaseOrder, closeActualCost, createPurchaseOrder, createReceipt, getTradeCycles,
  recordPurchasePayment,
} from "./tradeCycleApi";

jest.mock("./client", () => ({ apiRequest: jest.fn() }));
beforeEach(() => jest.clearAllMocks());

test("수입 사이클의 발주부터 판단까지 API 계약을 연결한다", () => {
  getTradeCycles();
  createPurchaseOrder({ costScenarioIds: ["scenario-1", "scenario-2"] });
  approvePurchaseOrder("order-1", 0);
  cancelPurchaseOrder("order-1", 1);
  recordPurchasePayment("order-1", { version: 1, paymentStatus: "PAID" });
  allocateShipment({ shipmentId: "shipment-1" });
  createReceipt({ shipmentId: "shipment-1" });
  addActualCost("order-1", { costType: "FREIGHT" });
  closeActualCost("order-1");
  addSalesObservation("order-1", { inventoryLotId: "lot-1" });
  addReorderDecision("order-1", { productId: "product-1", decision: "REORDER" });

  expect(apiRequest).toHaveBeenNthCalledWith(1, "/trade-cycles/purchase-orders");
  expect(apiRequest).toHaveBeenNthCalledWith(2, "/trade-cycles/purchase-orders", expect.objectContaining({ method: "POST" }));
  expect(apiRequest).toHaveBeenNthCalledWith(3, "/trade-cycles/purchase-orders/order-1/approve", { method: "POST", body: JSON.stringify({ version: 0 }) });
  expect(apiRequest).toHaveBeenNthCalledWith(6, "/trade-cycles/shipment-allocations", expect.objectContaining({ method: "POST" }));
  expect(apiRequest).toHaveBeenNthCalledWith(9, "/trade-cycles/purchase-orders/order-1/cost-close", { method: "POST" });
  expect(apiRequest).toHaveBeenNthCalledWith(11, "/trade-cycles/purchase-orders/order-1/decisions", expect.objectContaining({ method: "POST" }));
});
