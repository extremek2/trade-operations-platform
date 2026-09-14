import { apiRequest } from "./client";
import { changeQuoteStatus, createQuoteRevision, updateQuote } from "./quoteApi";

jest.mock("./client", () => ({ apiRequest: jest.fn() }));

beforeEach(() => jest.clearAllMocks());

test("DRAFT 수정 API는 PUT과 전체 payload를 사용한다", async () => {
  const payload = { currency: "USD", version: 2, lines: [{ productId: "product-1" }] };
  updateQuote("quote-1", payload);
  expect(apiRequest).toHaveBeenCalledWith("/quotes/quote-1", {
    method: "PUT",
    body: JSON.stringify(payload),
  });
});

test("새 개정본과 상태 전이 API 경로를 구분한다", async () => {
  const revision = { currency: "USD", version: 3, lines: [{ productId: "product-1" }] };
  const status = { status: "RECEIVED", version: 3 };
  createQuoteRevision("quote-1", revision);
  changeQuoteStatus("quote-1", status);

  expect(apiRequest).toHaveBeenNthCalledWith(1, "/quotes/quote-1/revisions", {
    method: "POST",
    body: JSON.stringify(revision),
  });
  expect(apiRequest).toHaveBeenNthCalledWith(2, "/quotes/quote-1/status", {
    method: "POST",
    body: JSON.stringify(status),
  });
});
