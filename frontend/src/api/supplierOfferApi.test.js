import { apiRequest } from "./client";
import { createDocumentDraft, getSupplierDocumentStatus, getWholesaleCatalogStatus, importSupplierDocument,
  importSupplierOfferFile, retrySupplierDocumentExtraction } from "./supplierOfferApi";

jest.mock("./client", () => ({ apiRequest: jest.fn() }));

beforeEach(() => jest.clearAllMocks());

test("도매 사이트 라이브 설정 상태를 서버에서 조회한다", () => {
  getWholesaleCatalogStatus();

  expect(apiRequest).toHaveBeenCalledWith("/wholesale-catalog/status");
});

test("공급 제안 파일을 multipart 필드로 조립한다", () => {
  const file = new File(["상품명,단가\n테스트,100"], "offer.csv", { type: "text/csv" });

  importSupplierOfferFile({ supplierId: "supplier-1", currency: "JPY", sourceReference: "mail-42", file });

  const [path, options] = apiRequest.mock.calls[0];
  expect(path).toBe("/supplier-offer-drafts/import");
  expect(options.method).toBe("POST");
  expect(options.body).toBeInstanceOf(FormData);
  expect(options.body.get("supplierId")).toBe("supplier-1");
  expect(options.body.get("currency")).toBe("JPY");
  expect(options.body.get("sourceReference")).toBe("mail-42");
  expect(options.body.get("file")).toEqual(file);
});

test("문서 업로드와 검토 초안 생성을 별도 요청으로 보낸다", () => {
  apiRequest.mockClear();
  const file = new File(["%PDF-test"], "quotation.pdf", { type: "application/pdf" });
  importSupplierDocument({ supplierId: "supplier-1", sourceReference: "메일 첨부", file });
  createDocumentDraft("document-1", "JPY");

  const [uploadPath, uploadOptions] = apiRequest.mock.calls[0];
  expect(uploadPath).toBe("/supplier-documents/import");
  expect(uploadOptions.body.get("supplierId")).toBe("supplier-1");
  expect(uploadOptions.body.get("file")).toEqual(file);
  expect(apiRequest).toHaveBeenLastCalledWith("/supplier-documents/document-1/draft", {
    method: "POST", body: JSON.stringify({ currency: "JPY" }),
  });
});

test("문서 기능 상태를 조회하고 실패한 추출을 재시도한다", () => {
  getSupplierDocumentStatus();
  retrySupplierDocumentExtraction("document-1");

  expect(apiRequest).toHaveBeenNthCalledWith(1, "/supplier-documents/status");
  expect(apiRequest).toHaveBeenNthCalledWith(2, "/supplier-documents/document-1/extractions/retry", { method: "POST" });
});
