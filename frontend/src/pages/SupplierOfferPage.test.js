import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { getBusinessPartners } from "../api/partnerApi";
import { getProducts } from "../api/productApi";
import { createDocumentDraft, createPurchaseSelection, createSupplierOfferDraft, getPurchaseSelections,
  getSupplierDocuments, getSupplierDocumentStatus, getSupplierOfferDrafts, importSupplierDocument, importSupplierOfferFile,
  retrySupplierDocumentExtraction } from "../api/supplierOfferApi";
import { getSupplierDocumentContent, reviewSupplierOfferDraft } from "../api/supplierOfferApi";
import { searchWholesaleCatalog, getWholesaleCatalogItem, getWholesaleCatalogStatus, importWholesaleCatalogItem } from "../api/supplierOfferApi";
import SupplierOfferPage from "./SupplierOfferPage";

jest.mock("../api/partnerApi", () => ({ getBusinessPartners: jest.fn() }));
jest.mock("../api/productApi", () => ({ getProducts: jest.fn() }));
jest.mock("../api/supplierOfferApi", () => ({
  confirmSupplierOfferDraft: jest.fn(), createDocumentDraft: jest.fn(), createPurchaseSelection: jest.fn(),
  createSupplierOfferDraft: jest.fn(), excludeSupplierOfferLine: jest.fn(),
  getPurchaseSelections: jest.fn(), getSupplierDocuments: jest.fn(), getSupplierDocumentContent: jest.fn(),
  getSupplierDocumentStatus: jest.fn(),
  getSupplierOfferDrafts: jest.fn(), importSupplierDocument: jest.fn(), importSupplierOfferFile: jest.fn(), reviewSupplierOfferLine: jest.fn(),
  reviewSupplierOfferDraft: jest.fn(),
  retrySupplierDocumentExtraction: jest.fn(),
  searchWholesaleCatalog: jest.fn(), getWholesaleCatalogItem: jest.fn(), getWholesaleCatalogStatus: jest.fn(), importWholesaleCatalogItem: jest.fn(),
}));
jest.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: { role: "OWNER" } }) }));

const draft = {
  draftId: "draft-1", supplierName: "오사카 공급처", supplierId: "supplier-1",
  status: "CONFIRMED", version: 4, currency: "JPY", sourceType: "MANUAL", originalText: "A, B, C 원본",
  lines: [
    { lineNumber: 1, originalName: "원본 A", reviewedName: "확인 A", status: "CONFIRMED", quantityUnit: "EA", minimumQuantity: 10,
      availableQuantity: 30, unitPrice: 280,
      priceTiers: [{ minimumQuantity: 10, unitPrice: 280 }, { minimumQuantity: 20, unitPrice: 250 }] },
    { lineNumber: 2, originalName: "원본 B", reviewedName: "확인 B", status: "CONFIRMED", quantityUnit: "EA", minimumQuantity: 5, unitPrice: 190 },
    { lineNumber: 3, originalName: "원본 C", reviewedName: null, status: "EXCLUDED", quantityUnit: null, minimumQuantity: null, unitPrice: null },
  ],
};

beforeEach(() => {
  jest.clearAllMocks();
  getBusinessPartners.mockResolvedValue([{ businessPartnerId: "supplier-1", name: "오사카 공급처", roles: ["SUPPLIER"] }]);
  getProducts.mockResolvedValue([]);
  getSupplierOfferDrafts.mockResolvedValue([draft]);
  getSupplierDocuments.mockResolvedValue([]);
  getSupplierDocumentStatus.mockResolvedValue({ storageEnabled: true, extractionEnabled: true });
  getPurchaseSelections.mockResolvedValue([]);
  createPurchaseSelection.mockResolvedValue({});
  createSupplierOfferDraft.mockResolvedValue({});
  importSupplierOfferFile.mockResolvedValue({});
  importSupplierDocument.mockResolvedValue({});
  retrySupplierDocumentExtraction.mockResolvedValue({});
  createDocumentDraft.mockResolvedValue({});
  getSupplierDocumentContent.mockResolvedValue(new Blob(["%PDF-sample"], { type: "application/pdf" }));
  reviewSupplierOfferDraft.mockResolvedValue({});
  getWholesaleCatalogStatus.mockResolvedValue({ liveConfigured: false, platform: "DOMEGGOOK" });
  searchWholesaleCatalog.mockResolvedValue([]);
  getWholesaleCatalogItem.mockResolvedValue({});
  importWholesaleCatalogItem.mockResolvedValue({});
  URL.createObjectURL = jest.fn(() => "blob:document-preview");
  URL.revokeObjectURL = jest.fn();
});

test("서버 API 키가 없으면 실제 도매 조회를 비활성화한다", async () => {
  render(<SupplierOfferPage navigate={jest.fn()}/>);

  const searchForm = await screen.findByRole("form", { name: "도매 상품 검색" });
  expect(within(searchForm).getByRole("option", { name: "도매꾹 실제 API · 서버 키 필요" })).toBeDisabled();
  expect(await screen.findByText("서버에 도매꾹 API 키가 없어 실제 API 조회는 비활성화되어 있습니다.")).toBeInTheDocument();
});

test("서버 API 키가 설정되면 실제 도매 조회를 선택할 수 있다", async () => {
  getWholesaleCatalogStatus.mockResolvedValue({ liveConfigured: true, platform: "DOMEGGOOK" });
  render(<SupplierOfferPage navigate={jest.fn()}/>);

  const searchForm = await screen.findByRole("form", { name: "도매 상품 검색" });
  await waitFor(() => expect(within(searchForm)
    .getByRole("option", { name: "도매꾹 실제 API · 서버 키 필요" })).toBeEnabled());
});

test("문서 저장소가 꺼져 있으면 업로드 폼 대신 운영 상태를 표시한다", async () => {
  getSupplierDocumentStatus.mockResolvedValue({ storageEnabled: false, extractionEnabled: false });
  render(<SupplierOfferPage navigate={jest.fn()}/>);

  expect(await screen.findByText("문서 저장소가 비활성화되어 있습니다.")).toBeInTheDocument();
  expect(screen.queryByRole("form", { name: "공급 문서 가져오기" })).not.toBeInTheDocument();
});

test("실패한 문서의 텍스트 추출을 화면에서 재시도한다", async () => {
  getSupplierDocuments.mockResolvedValue([{ documentId: "doc-failed", originalFileName: "failed.pdf",
    sourceType: "PDF", extractionStatus: "FAILED", errorMessage: "OCR 일시 실패" }]);
  render(<SupplierOfferPage navigate={jest.fn()}/>);

  fireEvent.click(await screen.findByRole("button", { name: "텍스트 추출 재시도" }));
  await waitFor(() => expect(retrySupplierDocumentExtraction).toHaveBeenCalledWith("doc-failed"));
});

test("도매 사이트 샘플 상품을 상세 확인 후 검토 초안으로 가져온다", async () => {
  searchWholesaleCatalog.mockResolvedValue([{ itemId: "1002", title: "테스트 생활용품 박스", sellerId: "sample_household",
    unitPrice: 4500, minimumQuantity: 5, businessOnly: true, overseasShipping: false }]);
  getWholesaleCatalogItem.mockResolvedValue({ itemId: "1002", title: "테스트 생활용품 박스",
    sellerId: "sample_household", minimumQuantity: 5, priceTiers: [
      { minimumQuantity: 1, unitPrice: 4500 }, { minimumQuantity: 20, unitPrice: 4100 },
    ], unitPrice: null,
    availableQuantity: 85, shippingFee: "수량별 배송비", businessOnly: true, overseasShipping: false,
    fetchedAt: "2026-09-22T17:00:00", productUrl: null });
  render(<SupplierOfferPage navigate={jest.fn()}/>);
  const searchForm = screen.getByRole("form", { name: "도매 상품 검색" });
  fireEvent.click(within(searchForm).getByRole("button", { name: "상품 조회" }));
  await waitFor(() => expect(searchWholesaleCatalog).toHaveBeenCalledWith("테스트", "SAMPLE"));
  fireEvent.click(await screen.findByRole("button", { name: "상세 조건 확인" }));
  expect(await screen.findByText(/수량별 단가 1개 이상 4,500원 \/ 20개 이상 4,100원/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "검토 초안으로 가져오기" }));
  await waitFor(() => expect(importWholesaleCatalogItem).toHaveBeenCalledWith("1002", "SAMPLE"));
});

test("문서 품목의 불확실한 수량을 수정하고 대조한 뒤 한 번에 확인한다", async () => {
  getSupplierDocuments.mockResolvedValue([{ documentId: "doc-1", originalFileName: "sample.pdf",
    sourceType: "PDF", extractionStatus: "SUCCEEDED", confidence: 82 }]);
  getSupplierOfferDrafts.mockResolvedValue([{ ...draft, draftId: "document-draft", sourceArtifactId: "doc-1",
    sourceType: "PDF", originalFileName: "sample.pdf", extractionMethod: "OCR", status: "REVIEW_REQUIRED", version: 0,
    lines: [
      { lineNumber: 1, originalName: "JP-001 텀블러 1O EA 280 JP", sourceLocation: "텍스트 1행",
        reviewedName: "텀블러", supplierSku: "JP-001", minimumQuantity: 10, quantityUnit: "EA", unitPrice: 280,
        countryOfOrigin: "JP", extractionConfidence: 72.5, status: "PENDING" },
      { lineNumber: 2, originalName: "배송 조건", sourceLocation: "텍스트 2행", status: "PENDING" },
    ] }]);
  render(<SupplierOfferPage navigate={jest.fn()}/>);
  const form = await screen.findByRole("form", { name: "오사카 공급처 문서 검토" });
  expect(within(form).getByText("원본 대조 필요 · 72.5%")).toBeInTheDocument();
  expect(within(form).queryByRole("checkbox", { name: "행 2" })).not.toBeInTheDocument();
  fireEvent.click(within(form).getByRole("button", { name: "상품 외 문구 1행 보기" }));
  expect(within(form).getByRole("checkbox", { name: "행 2" })).not.toBeChecked();
  const next = within(form).getByRole("button", { name: "검토 완료하고 구매 후보 선택으로" });
  expect(next).toBeDisabled();
  fireEvent.change(within(form).getByLabelText("행 1 수량"), { target: { value: "24" } });
  fireEvent.click(within(form).getByRole("checkbox", { name: "행 1 대조 완료" }));
  expect(next).toBeEnabled();
  fireEvent.click(next);
  await waitFor(() => expect(reviewSupplierOfferDraft).toHaveBeenCalledWith("document-draft", {
    version: 0, lines: [
      expect.objectContaining({ lineNumber: 1, decision: "CONFIRM", minimumQuantity: 24, quantityUnit: "EA" }),
      { lineNumber: 2, decision: "EXCLUDE" },
    ],
  }));
  expect(getSupplierDocumentContent).toHaveBeenCalledWith("doc-1");
});

test("문서 검토 완료 후 같은 문서 카드에서 구매 후보를 선택한다", async () => {
  getSupplierDocuments.mockResolvedValue([{ documentId: "doc-1", originalFileName: "sample.pdf",
    sourceType: "PDF", extractionStatus: "SUCCEEDED" }]);
  getSupplierOfferDrafts.mockResolvedValue([{ ...draft, sourceType: "PDF", sourceArtifactId: "doc-1",
    originalFileName: "sample.pdf" }]);
  render(<SupplierOfferPage navigate={jest.fn()}/>);
  const documentCard = (await screen.findByText("다음 단계 · 구매 후보 선택")).closest("article");
  expect(within(documentCard).getByRole("form", { name: "오사카 공급처 구매 품목 선택" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "제안 검토 원장" })).not.toBeInTheDocument();
});

test("PDF 문서를 보관하고 추출 결과로 검토 초안을 만든다", async () => {
  const document = { documentId: "document-1", originalFileName: "quotation.pdf", sourceType: "PDF",
    extractionStatus: "SUCCEEDED", extractedText: "JP-001 스테인리스 텀블러", confidence: 100 };
  getSupplierDocuments.mockResolvedValue([document]);
  render(<SupplierOfferPage navigate={jest.fn()}/>);

  const form = await screen.findByRole("form", { name: "공급 문서 가져오기" });
  const file = new File(["%PDF-test"], "quotation.pdf", { type: "application/pdf" });
  fireEvent.change(within(form).getByLabelText("공급처"), { target: { value: "supplier-1" } });
  fireEvent.change(within(form).getByLabelText(/문서 파일/), { target: { files: [file] } });
  fireEvent.click(within(form).getByRole("button", { name: "원본 보관하고 텍스트 추출" }));
  await waitFor(() => expect(importSupplierDocument).toHaveBeenCalledWith({
    supplierId: "supplier-1", sourceReference: undefined, file,
  }));
  expect(screen.getByText("JP-001 스테인리스 텀블러")).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("quotation.pdf 통화"), { target: { value: "usd" } });
  fireEvent.click(screen.getByRole("button", { name: "문서 검토 초안 만들기" }));
  await waitFor(() => expect(createDocumentDraft).toHaveBeenCalledWith("document-1", "USD"));
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
  expect(within(first).getByLabelText("희망 수량")).toHaveAttribute("max", "30");
  expect(within(first).getByText(/조회 당시 재고 30 EA/)).toBeInTheDocument();
  expect(within(first).getByText(/희망 수량 적용 단가 KRW 280/)).toBeInTheDocument();
  fireEvent.change(within(first).getByLabelText("희망 수량"), { target: { value: "24" } });
  expect(within(first).getByText(/희망 수량 적용 단가 KRW 250/)).toBeInTheDocument();
  fireEvent.click(within(form).getByRole("button", { name: "선택 행을 견적으로 넘기기" }));

  await waitFor(() => expect(createPurchaseSelection).toHaveBeenCalledWith("draft-1", {
    version: 4, lines: [{ lineNumber: 1, desiredQuantity: 24 }],
  }));
  expect(within(form).queryByText("확인 행 3 ·")).not.toBeInTheDocument();
});
