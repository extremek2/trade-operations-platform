import { apiRequest } from "./client";
import { createCostScenario, getCostScenarios } from "./costingApi";

jest.mock("./client", () => ({ apiRequest: jest.fn() }));

beforeEach(() => jest.clearAllMocks());

test("원가 시나리오 목록과 생성 API를 호출한다", () => {
  const payload = { quoteId: "quote-1", productId: "product-1", orderQuantity: 10 };
  getCostScenarios();
  createCostScenario(payload);
  expect(apiRequest).toHaveBeenNthCalledWith(1, "/cost-scenarios");
  expect(apiRequest).toHaveBeenNthCalledWith(2, "/cost-scenarios", {
    method: "POST", body: JSON.stringify(payload),
  });
});
