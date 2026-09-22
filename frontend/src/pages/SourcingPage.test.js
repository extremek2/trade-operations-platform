import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { createBusinessPartner, getBusinessPartners, updateSupplierInquiryContacts } from "../api/partnerApi";
import { getProducts } from "../api/productApi";
import {
  changeQuoteStatus,
  createQuote,
  createQuoteRevision,
  getQuotes,
  updateQuote,
} from "../api/quoteApi";
import SourcingPage from "./SourcingPage";

jest.mock("../api/partnerApi", () => ({
  createBusinessPartner: jest.fn(),
  getBusinessPartners: jest.fn(),
  updateSupplierInquiryContacts: jest.fn(),
}));
jest.mock("../api/productApi", () => ({ getProducts: jest.fn() }));
jest.mock("../api/quoteApi", () => ({
  changeQuoteStatus: jest.fn(),
  createQuote: jest.fn(),
  createQuoteRevision: jest.fn(),
  getQuotes: jest.fn(),
  updateQuote: jest.fn(),
}));
jest.mock("../context/AuthContext", () => ({
  useAuth: () => ({ user: { role: "OWNER" } }),
}));

const suppliers = [{ businessPartnerId: "supplier-1", name: "서울 공급사", roles: ["SUPPLIER"] }];
const products = [
  { productId: "product-1", name: "텀블러", status: "SOURCING" },
  { productId: "product-2", name: "파우치", status: "REVIEWING" },
];
const lines = [
  {
    lineNumber: 1, productId: "product-1", productName: "텀블러", supplierSku: "SUP-01",
    description: "무광 텀블러", minimumQuantity: 100, quantityUnit: "EA", unitPrice: 2.5,
    countryOfOrigin: "CN", leadTimeDays: 14, notes: "검정 포장",
  },
  {
    lineNumber: 2, productId: "product-2", productName: "파우치", supplierSku: null,
    description: "면 파우치", minimumQuantity: null, quantityUnit: "EA", unitPrice: null,
    countryOfOrigin: null, leadTimeDays: null, notes: null,
  },
];

function quote(overrides = {}) {
  return {
    quoteId: "quote-1",
    previousQuoteId: null,
    revisionNumber: 1,
    supplierId: "supplier-1",
    supplierName: "서울 공급사",
    quoteNumber: "Q-2026-001",
    currency: "USD",
    status: "DRAFT",
    quotedAt: "2026-09-10",
    validUntil: "2026-10-10",
    notes: "첫 제안",
    version: 3,
    lines,
    ...overrides,
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  getBusinessPartners.mockResolvedValue(suppliers);
  getProducts.mockResolvedValue(products);
  getQuotes.mockResolvedValue([quote()]);
  createBusinessPartner.mockResolvedValue({});
  updateSupplierInquiryContacts.mockResolvedValue({});
  createQuote.mockResolvedValue({});
  updateQuote.mockResolvedValue({});
  createQuoteRevision.mockResolvedValue({});
  changeQuoteStatus.mockResolvedValue({});
});

test("공급처 문의 받는 사람과 참조를 등록한다", async () => {
  getBusinessPartners.mockResolvedValue([{ ...suppliers[0], inquiryEmail: "sales@example.test",
    inquiryCcEmails: ["buyer@example.test"], version: 2 }]);
  render(<SourcingPage/>);
  await screen.findByRole("form", { name: "상품 문의 초안 준비" });

  fireEvent.change(screen.getByLabelText("공급처명"), { target: { value: "신규 공급사" } });
  fireEvent.change(screen.getAllByLabelText(/상품 문의 이메일 · 받는 사람/)[0], { target: { value: "new@example.test" } });
  fireEvent.change(screen.getAllByLabelText(/참조 이메일/)[0], { target: { value: "one@example.test, two@example.test" } });
  fireEvent.click(screen.getByRole("button", { name: "공급처 등록" }));
  await waitFor(() => expect(createBusinessPartner).toHaveBeenCalledWith({
    name: "신규 공급사", roles: ["SUPPLIER"], inquiryEmail: "new@example.test",
    inquiryCcEmails: ["one@example.test", "two@example.test"],
  }));
});

test("선택한 상품과 공급처 주소로 Gmail 문의 초안을 만들고 본문 수정을 반영한다", async () => {
  getBusinessPartners.mockResolvedValue([{ ...suppliers[0], inquiryEmail: "sales@example.test",
    inquiryCcEmails: ["buyer@example.test"], version: 2 }]);
  render(<SourcingPage/>);
  const form = await screen.findByRole("form", { name: "상품 문의 초안 준비" });
  expect(screen.queryByRole("link", { name: "Gmail 작성 창 열기" })).not.toBeInTheDocument();
  fireEvent.change(within(form).getByLabelText("공급처"), { target: { value: "supplier-1" } });
  fireEvent.click(within(form).getByLabelText("텀블러"));
  fireEvent.click(within(form).getByLabelText("파우치"));
  fireEvent.click(within(form).getByRole("button", { name: "문의 초안 만들기" }));

  const preview = screen.getByLabelText("상품 문의 초안 미리보기");
  expect(within(preview).getByText("sales@example.test")).toBeInTheDocument();
  const link = within(preview).getByRole("link", { name: "Gmail 작성 창 열기" });
  const url = new URL(link.getAttribute("href"));
  expect(url.searchParams.get("to")).toBe("sales@example.test");
  expect(url.searchParams.get("cc")).toBe("buyer@example.test");
  expect(url.searchParams.get("body")).toContain("텀블러");
  expect(url.searchParams.get("body")).toContain("파우치");

  fireEvent.change(within(preview).getByLabelText("본문"), { target: { value: "수정한 상품 문의 본문" } });
  expect(new URL(within(preview).getByRole("link", { name: "Gmail 작성 창 열기" }).getAttribute("href"))
    .searchParams.get("body")).toBe("수정한 상품 문의 본문");
  fireEvent.click(within(form).getByLabelText("파우치"));
  expect(screen.queryByLabelText("상품 문의 초안 미리보기")).not.toBeInTheDocument();
});

test("문의 주소가 없는 공급처는 초안 대상으로 선택할 수 없다", async () => {
  render(<SourcingPage/>);
  const form = await screen.findByRole("form", { name: "상품 문의 초안 준비" });
  expect(within(form).getByRole("option", { name: /문의 주소 미등록/ })).toBeDisabled();
  expect(within(form).getByRole("button", { name: "문의 초안 만들기" })).toBeDisabled();
});

test("기존 공급처의 문의 주소와 참조를 수정한다", async () => {
  getBusinessPartners.mockResolvedValue([{ ...suppliers[0], inquiryEmail: "sales@example.test",
    inquiryCcEmails: ["old@example.test"], version: 2 }]);
  render(<SourcingPage/>);
  fireEvent.click(await screen.findByRole("button", { name: "문의 주소 수정" }));
  const editor = screen.getByRole("form", { name: "서울 공급사 문의 주소 수정" });
  fireEvent.change(within(editor).getByLabelText(/참조 이메일/), { target: { value: "new@example.test" } });
  fireEvent.click(within(editor).getByRole("button", { name: "저장" }));
  await waitFor(() => expect(updateSupplierInquiryContacts).toHaveBeenCalledWith("supplier-1", {
    version: 2, inquiryEmail: "sales@example.test", inquiryCcEmails: ["new@example.test"],
  }));
});

test("DRAFT 수정은 기존 품목 행 전체와 optimistic-lock version을 보낸다", async () => {
  render(<SourcingPage/>);
  const entry = await screen.findByRole("article", { name: /서울 공급사 Q-2026-001 개정 1/ });

  fireEvent.click(within(entry).getByRole("button", { name: "내용 수정" }));
  const editor = screen.getByRole("form", { name: "견적 내용 수정" });
  const descriptions = within(editor).getAllByLabelText("품목 설명");
  expect(descriptions).toHaveLength(2);
  fireEvent.change(descriptions[0], { target: { value: "무광 텀블러 수정" } });
  fireEvent.click(within(editor).getByRole("button", { name: "수정 저장" }));

  await waitFor(() => expect(updateQuote).toHaveBeenCalledWith("quote-1", expect.objectContaining({
    quoteNumber: "Q-2026-001",
    currency: "USD",
    quotedAt: "2026-09-10",
    validUntil: "2026-10-10",
    version: 3,
    lines: [
      expect.objectContaining({ productId: "product-1", description: "무광 텀블러 수정" }),
      expect.objectContaining({ productId: "product-2", description: "면 파우치" }),
    ],
  })));
});

test("RECEIVED 이후 정정은 견적번호와 모든 행을 보존한 새 개정본으로 생성한다", async () => {
  getQuotes.mockResolvedValue([quote({ status: "RECEIVED", version: 4 })]);
  render(<SourcingPage/>);
  const entry = await screen.findByRole("article", { name: /서울 공급사 Q-2026-001 개정 1/ });

  fireEvent.click(within(entry).getByRole("button", { name: "새 개정본" }));
  const editor = screen.getByRole("form", { name: "새 개정본 작성" });
  expect(within(editor).getByLabelText(/견적번호/)).toBeDisabled();
  expect(within(editor).getAllByLabelText("품목 설명")).toHaveLength(2);
  fireEvent.click(within(editor).getByRole("button", { name: "개정본 저장" }));

  await waitFor(() => expect(createQuoteRevision).toHaveBeenCalled());
  const [quoteId, payload] = createQuoteRevision.mock.calls[0];
  expect(quoteId).toBe("quote-1");
  expect(payload).toEqual(expect.objectContaining({ currency: "USD", version: 4 }));
  expect(payload).not.toHaveProperty("quoteNumber");
  expect(payload.lines).toHaveLength(2);
});

test("현재 상태에서 도메인이 허용한 상태 전이만 제공한다", async () => {
  getQuotes.mockResolvedValue([
    quote(),
    quote({ quoteId: "quote-2", quoteNumber: "Q-2026-002", status: "RECEIVED", version: 7 }),
  ]);
  render(<SourcingPage/>);
  const draft = await screen.findByRole("article", { name: /Q-2026-001 개정 1/ });
  const received = screen.getByRole("article", { name: /Q-2026-002 개정 1/ });

  expect(within(draft).getByRole("button", { name: "수신 처리" })).toBeInTheDocument();
  expect(within(draft).getByRole("button", { name: "반려 처리" })).toBeInTheDocument();
  expect(within(draft).queryByRole("button", { name: "선택 처리" })).not.toBeInTheDocument();
  expect(within(received).getByRole("button", { name: "선택 처리" })).toBeInTheDocument();
  expect(within(received).getByRole("button", { name: "만료 처리" })).toBeInTheDocument();
  expect(within(received).queryByRole("button", { name: "수신 처리" })).not.toBeInTheDocument();

  fireEvent.click(within(draft).getByRole("button", { name: "수신 처리" }));
  await waitFor(() => expect(changeQuoteStatus).toHaveBeenCalledWith("quote-1", { status: "RECEIVED", version: 3 }));
});

test("개정 메타데이터와 이전 개정본을 견적 단위로 표시하고 중복 개정을 막는다", async () => {
  getQuotes.mockResolvedValue([
    quote({ status: "RECEIVED" }),
    quote({
      quoteId: "quote-2", previousQuoteId: "quote-1", revisionNumber: 2,
      status: "DRAFT", version: 0, notes: "정정 제안",
    }),
  ]);
  render(<SourcingPage/>);
  const first = await screen.findByRole("article", { name: /Q-2026-001 개정 1/ });
  const second = screen.getByRole("article", { name: /Q-2026-001 개정 2/ });

  expect(within(second).getByText("2026-09-10")).toBeInTheDocument();
  expect(within(second).getByText("2026-10-10")).toBeInTheDocument();
  expect(within(second).getByText("Q-2026-001 · 개정 1")).toBeInTheDocument();
  expect(within(first).queryByRole("button", { name: "새 개정본" })).not.toBeInTheDocument();
  expect(within(second).getAllByRole("row")).toHaveLength(3);
});
