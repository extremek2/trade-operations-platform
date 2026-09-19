import { apiRequest } from "./client";
import { importSupplierOfferFile } from "./supplierOfferApi";

jest.mock("./client", () => ({ apiRequest: jest.fn() }));

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
