import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { getCostScenarios } from "../api/costingApi";
import { getShipments } from "../api/shipmentApi";
import { approvePurchaseOrder, createPurchaseOrder, getTradeCycles } from "../api/tradeCycleApi";
import TradeCyclePage from "./TradeCyclePage";

jest.mock("../api/costingApi", () => ({ getCostScenarios: jest.fn() }));
jest.mock("../api/shipmentApi", () => ({ getShipments: jest.fn() }));
jest.mock("../api/tradeCycleApi", () => ({
  getTradeCycles: jest.fn(), createPurchaseOrder: jest.fn(), approvePurchaseOrder: jest.fn(),
  cancelPurchaseOrder: jest.fn(), recordPurchasePayment: jest.fn(), allocateShipment: jest.fn(),
  createReceipt: jest.fn(), addActualCost: jest.fn(), closeActualCost: jest.fn(),
  addSalesObservation: jest.fn(), addReorderDecision: jest.fn(),
}));
jest.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: { role: "OWNER" } }) }));

const scenario = {
  scenarioId: "scenario-1", scenarioName: "기준안", revisionNumber: 1, productName: "휴대용 선풍기",
  quoteId: "quote-1", orderQuantity: 10, calculationStatus: "COMPLETE", quoteStatus: "SELECTED",
};
const secondScenario = {
  ...scenario, scenarioId: "scenario-2", scenarioName: "파우치 기준안", productName: "보관 파우치", orderQuantity: 5,
};
const cycle = status => ({
  purchaseOrder: {
    purchaseOrderId: "order-1", orderNumber: "PO-001", status, paymentStatus: "UNPAID", paidAmount: 0,
    currency: "KRW", supplierName: "선전 공급사", expectedTotalCostKrw: 110000, version: status === "DRAFT" ? 0 : 1,
    lines: [{ purchaseOrderLineId: "line-1", productName: "휴대용 선풍기", orderedQuantity: 10,
      shippedQuantity: 0, receivedQuantity: 0, quantityUnit: "EA", unitPrice: 10000 }],
  },
  shipmentAllocations: [], inventoryLots: [], actualCosts: [], costClose: null,
  salesObservations: [], decisions: [],
});

beforeEach(() => {
  jest.clearAllMocks();
  getCostScenarios.mockResolvedValue([scenario, secondScenario]);
  getShipments.mockResolvedValue([]);
  getTradeCycles.mockResolvedValue([cycle("DRAFT")]);
  createPurchaseOrder.mockResolvedValue(cycle("DRAFT"));
  approvePurchaseOrder.mockResolvedValue(cycle("APPROVED"));
});

test("같은 견적의 완성 원가안 여러 개로 발주를 만들고 초안을 승인한다", async () => {
  render(<TradeCyclePage navigate={jest.fn()}/>);
  expect(await screen.findByText("PO-001")).toBeInTheDocument();

  const createForm = screen.getByRole("form", { name: "발주 생성" });
  fireEvent.click(within(createForm).getByLabelText(/휴대용 선풍기 · 기준안/));
  fireEvent.click(within(createForm).getByLabelText(/보관 파우치 · 파우치 기준안/));
  fireEvent.change(within(createForm).getByLabelText("발주번호"), { target: { value: "PO-002" } });
  fireEvent.click(within(createForm).getByRole("button", { name: "발주 초안 생성" }));
  await waitFor(() => expect(createPurchaseOrder).toHaveBeenCalledWith({ costScenarioIds: ["scenario-1", "scenario-2"], orderNumber: "PO-002" }));

  const approveButton = screen.getByRole("button", { name: "발주 승인" });
  await waitFor(() => expect(approveButton).not.toBeDisabled());
  fireEvent.click(approveButton);
  await waitFor(() => expect(approvePurchaseOrder).toHaveBeenCalledWith("order-1", 0));
  expect(await screen.findByText("결제 상태 기록")).toBeInTheDocument();
});
