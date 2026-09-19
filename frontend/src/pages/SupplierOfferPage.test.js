import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { getBusinessPartners } from "../api/partnerApi";
import { getProducts } from "../api/productApi";
import { createPurchaseSelection, createSupplierOfferDraft, getPurchaseSelections, getSupplierOfferDrafts, importSupplierOfferFile } from "../api/supplierOfferApi";
import SupplierOfferPage from "./SupplierOfferPage";

jest.mock("../api/partnerApi", () => ({ getBusinessPartners: jest.fn() }));
jest.mock("../api/productApi", () => ({ getProducts: jest.fn() }));
jest.mock("../api/supplierOfferApi", () => ({
  confirmSupplierOfferDraft: jest.fn(), createPurchaseSelection: jest.fn(),
  createSupplierOfferDraft: jest.fn(), excludeSupplierOfferLine: jest.fn(),
  getPurchaseSelections: jest.fn(), getSupplierOfferDrafts: jest.fn(), importSupplierOfferFile: jest.fn(), reviewSupplierOfferLine: jest.fn(),
}));
jest.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: { role: "OWNER" } }) }));

const draft = {
  draftId: "draft-1", supplierName: "오사카 공급처", supplierId: "supplier-1",
  status: "CONFIRMED", version: 4, currency: "JPY", sourceType: "MANUAL", originalText: "A, B, C 원본",
  lines: [
    { lineNumber: 1, originalName: "원본 A", reviewedName: "확인 A", status: "CONFIRMED", quantityUnit: "EA", minimumQuantity: 10, unitPrice: 280 },
    { lineNumber: 2, originalName: "원본 B", reviewedName: "확인 B", status: "CONFIRMED", quantityUnit: "EA", minimumQuantity: 5, unitPrice: 190 },
    { lineNumber: 3, originalName: "원본 C", reviewedName: null, status: "EXCLUDED", quantityUnit: null, minimumQuantity: null, unitPrice: null },
  ],
};

beforeEach(() => {
  jest.clearAllMocks();
  getBusinessPartners.mockResolvedValue([{ businessPartnerId: "supplier-1", name: "오사카 공급처", roles: ["SUPPLIER"] }]);
  getProducts.mockResolvedValue([]);
  getSupplierOfferDrafts.mockResolvedValue([draft]);
  getPurchaseSelections.mockResolvedValue([]);
  createPurchaseSelection.mockResolvedValue({});
  createSupplierOfferDraft.mockResolvedValue({});
  importSupplierOfferFile.mockResolvedValue({});
});

test("CSV/XLSX 파일과 기본 메타데이터를 가져오기 API에 보낸다", async () => {
  render(<SupplierOfferPage navigate={jest.fn()}/>);
  const form = await screen.findByRole("form", { name: "공급 제안 파일 가져오기" });
  const file = new File(["상품명,단가\n테스트,100"], "offer.csv", { type: "text/csv" });
  fireEvent.change(within(form).getByLabelText("공급처"), { target: { value: "supplier-1" } });
  fireEvent.change(within(form).getByLabelText(/제안 파일/), { target: { files: [file] } });
  fireEvent.click(within(form).getByRole("button", { name: "파일 분석해 검토 초안 만들기" }));
  await waitFor(() => expect(importSupplierOfferFile).toHaveBeenCalledWith({
    supplierId: "supplier-1", currency: "JPY", sourceReference: undefined, file,
  }));
});

test("수동 원본과 상품명 행을 검토 초안으로 등록한다", async () => {
  render(<SupplierOfferPage navigate={jest.fn()}/>);
  const form = await screen.findByRole("form", { name: "수동 공급 제안 등록" });
  fireEvent.change(within(form).getByLabelText("공급처"), { target: { value: "supplier-1" } });
  fireEvent.change(within(form).getByLabelText("받은 원본 내용"), { target: { value: "A 280엔\nB 가격 미정" } });
  fireEvent.change(within(form).getByLabelText("원본 상품명 · 한 줄에 한 행"), { target: { value: "원본 A\n\n원본 B" } });
  fireEvent.click(within(form).getByRole("button", { name: "검토 초안 만들기" }));
  await waitFor(() => expect(createSupplierOfferDraft).toHaveBeenCalledWith({
    supplierId: "supplier-1", sourceReference: undefined, originalText: "A 280엔\nB 가격 미정",
    currency: "JPY", lines: [{ originalName: "원본 A" }, { originalName: "원본 B" }],
  }));
});

test("확인된 행 중 선택한 행과 희망 수량만 구매 선택 API에 보낸다", async () => {
  render(<SupplierOfferPage navigate={jest.fn()}/>);
  const form = await screen.findByRole("form", { name: "오사카 공급처 구매 품목 선택" });
  const first = within(form).getByText("확인 행 1 · 확인 A").closest("fieldset");
  fireEvent.click(within(first).getByRole("checkbox"));
  fireEvent.change(within(first).getByLabelText("희망 수량"), { target: { value: "24" } });
  fireEvent.click(within(form).getByRole("button", { name: "선택 행을 견적으로 넘기기" }));

  await waitFor(() => expect(createPurchaseSelection).toHaveBeenCalledWith("draft-1", {
    version: 4, lines: [{ lineNumber: 1, desiredQuantity: 24 }],
  }));
  expect(within(form).queryByText("확인 행 3 ·")).not.toBeInTheDocument();
});
