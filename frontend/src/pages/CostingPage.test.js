import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createCostScenario, getCostScenarios } from "../api/costingApi";
import { getQuotes } from "../api/quoteApi";
import { getPurchaseSelections } from "../api/supplierOfferApi";
import CostingPage from "./CostingPage";

jest.mock("../api/costingApi", () => ({
  createCostScenario: jest.fn(),
  getCostScenarios: jest.fn(),
}));
jest.mock("../api/quoteApi", () => ({ getQuotes: jest.fn() }));
jest.mock("../api/supplierOfferApi", () => ({ getPurchaseSelections: jest.fn() }));
jest.mock("../context/AuthContext", () => ({
  useAuth: () => ({ user: { role: "OWNER" } }),
}));

const quote = {
  quoteId: "quote-1", revisionNumber: 2, quoteNumber: "Q-JP-01", supplierName: "오사카 공급사",
  currency: "JPY", status: "RECEIVED", lines: [{
    lineNumber: 1, productId: "product-1", productName: "테스트 상품", description: "견적 품목",
    minimumQuantity: 10, quantityUnit: "EA", unitPrice: 500,
  }],
};
const scenario = {
  scenarioId: "scenario-1", previousScenarioId: null, revisionNumber: 1, scenarioName: "기준안",
  quoteId: "quote-1", quoteNumber: "Q-JP-01", quoteRevisionNumber: 2, supplierName: "오사카 공급사",
  productId: "product-1", productName: "테스트 상품", quoteCurrency: "JPY", quotedUnitPrice: 500,
  quotedUnitPriceSource: "SUPPLIER_QUOTE", orderQuantity: 10, excludedQuantity: 1, sellableQuantity: 9,
  exchangeRate: 9.1, exchangeRateSource: "MARKET_REFERENCE", taxableFreightKrw: 10000,
  customsClearanceCostKrw: 1000, inspectionCostKrw: 0, warehouseCostKrw: 0,
  domesticDeliveryCostKrw: 3000, otherCostKrw: 0, logisticsCostSource: "USER_ASSUMPTION",
  customsDutyRate: 8, vatRate: 10, taxRateSource: "PROFESSIONAL_CONFIRMATION",
  vatTreatment: "RECOVERABLE_EXCLUDED", targetSellingPriceKrw: 15000, sellingFeeRate: 10,
  variableCostPerUnitKrw: 1000, salesAssumptionSource: "USER_ASSUMPTION",
  calculationRuleVersion: "COST_V1", calculationStatus: "COMPLETE", totalCashRequiredKrw: 70000,
  totalLandedCostKrw: 65000, unitLandedCostKrw: 7222.22, vatKrw: 5000,
  contributionMarginPerUnitKrw: 5277.78,
};

beforeEach(() => {
  jest.clearAllMocks();
  window.scrollTo = jest.fn();
  getQuotes.mockResolvedValue([quote]);
  getCostScenarios.mockResolvedValue([scenario]);
  getPurchaseSelections.mockResolvedValue([]);
  createCostScenario.mockResolvedValue({});
});

test("구매 선택의 희망 수량을 예상 원가 입력에 가져온다", async () => {
  getPurchaseSelections.mockResolvedValue([{ quoteId: "quote-1", lines: [{ productId: "product-1", desiredQuantity: 24 }] }]);
  render(<CostingPage/>);
  const form = await screen.findByRole("form", { name: "원가 시나리오 입력" });
  fireEvent.change(within(form).getByLabelText("견적 품목"), { target: { value: "quote-1:product-1" } });
  expect(within(form).getByLabelText("주문수량")).toHaveValue(24);
});

test("저장된 decimal 결과와 입력 출처·부가세 관점을 비교한다", async () => {
  render(<CostingPage/>);
  const row = (await screen.findByText("기준안")).closest("tr");
  expect(within(row).getByText("계산 완료")).toBeInTheDocument();
  expect(within(row).getByText("70,000원")).toBeInTheDocument();
  expect(within(row).getByText("7,222.22원")).toBeInTheDocument();
  expect(within(row).getByText("5,277.78원")).toBeInTheDocument();
  expect(within(row).getByText("시장 참고값")).toBeInTheDocument();
  expect(within(row).getByText("전문가 확인값")).toBeInTheDocument();
  expect(within(row).getByText("회수 가능 가정 · 원가 제외")).toBeInTheDocument();
});

test("수신 견적 품목과 사용자 가정을 계산 API에 연결한다", async () => {
  getCostScenarios.mockResolvedValue([]);
  render(<CostingPage/>);
  const form = await screen.findByRole("form", { name: "원가 시나리오 입력" });
  fireEvent.change(within(form).getByLabelText("견적 품목"), { target: { value: "quote-1:product-1" } });
  fireEvent.change(within(form).getByLabelText("원화 환율"), { target: { value: "9.1" } });
  fireEvent.change(within(form).getByLabelText("목표 판매가"), { target: { value: "15000" } });
  fireEvent.click(within(form).getByRole("button", { name: "계산·저장" }));

  await waitFor(() => expect(createCostScenario).toHaveBeenCalledWith(expect.objectContaining({
    quoteId: "quote-1", productId: "product-1", scenarioName: "테스트 상품 기준안",
    orderQuantity: 10, excludedQuantity: 0, exchangeRate: 9.1,
    exchangeRateSource: "USER_ASSUMPTION", taxableFreightKrw: 0,
    customsDutyRate: 0, vatRate: 10, vatTreatment: "RECOVERABLE_EXCLUDED",
    targetSellingPriceKrw: 15000, sellingFeeRate: 0, variableCostPerUnitKrw: 0,
  })));
});

test("기존 스냅샷을 덮어쓰지 않고 previousScenarioId를 가진 새 버전을 만든다", async () => {
  render(<CostingPage/>);
  const row = (await screen.findByText("기준안")).closest("tr");
  fireEvent.click(within(row).getByRole("button", { name: "새 버전" }));
  const form = screen.getByRole("form", { name: "원가 시나리오 입력" });
  expect(within(form).getByLabelText("견적 품목")).toBeDisabled();
  fireEvent.click(within(form).getByRole("button", { name: "새 버전 계산·저장" }));

  await waitFor(() => expect(createCostScenario).toHaveBeenCalledWith(expect.objectContaining({
    previousScenarioId: "scenario-1", quoteId: "quote-1", productId: "product-1",
  })));
});
