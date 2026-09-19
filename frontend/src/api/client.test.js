import { refreshAccessToken } from "./client";

afterEach(() => jest.restoreAllMocks());

test("동시 세션 갱신 요청은 하나의 토큰 교환을 공유한다", async () => {
  let resolveFetch;
  jest.spyOn(global, "fetch").mockImplementation(() => new Promise(resolve => { resolveFetch = resolve; }));
  const first = refreshAccessToken();
  const second = refreshAccessToken();
  expect(global.fetch).toHaveBeenCalledTimes(1);
  resolveFetch({ ok: true, json: async () => ({ data: { accessToken: "new-token" } }) });
  expect(await first).toEqual({ accessToken: "new-token" });
  expect(await second).toEqual({ accessToken: "new-token" });
});

test("실패한 갱신 후에는 새 요청을 실행할 수 있다", async () => {
  jest.spyOn(global, "fetch").mockRejectedValueOnce(new Error("network"))
    .mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) });
  await expect(refreshAccessToken()).rejects.toThrow("network");
  expect(await refreshAccessToken()).toBeNull();
  expect(global.fetch).toHaveBeenCalledTimes(2);
});

test("인증된 auth/me 요청도 access 토큰이 만료되면 갱신 후 재시도한다", async () => {
  const { apiRequest, setAccessToken } = require("./client");
  setAccessToken("expired");
  jest.spyOn(global, "fetch").mockResolvedValueOnce({ ok: false, status: 401, json: async () => ({}) })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ data: { accessToken: "renewed", user: { sessionKind: "ACCOUNT" } } }) })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ data: { email: "user@example.test" } }) });
  expect(await apiRequest("/auth/me")).toEqual({ email: "user@example.test" });
  expect(global.fetch).toHaveBeenCalledTimes(3);
  expect(global.fetch.mock.calls[2][1].headers.Authorization).toBe("Bearer renewed");
});

test("갱신 서버의 일시 장애는 세션 만료로 처리하지 않는다", async () => {
  const { subscribeSession } = require("./client");
  const listener = jest.fn(); const unsubscribe = subscribeSession(listener);
  try {
    jest.spyOn(global, "fetch").mockResolvedValue({ ok: false, status: 503, json: async () => ({ message: "잠시 후 다시 시도" }) });
    await expect(refreshAccessToken()).rejects.toMatchObject({ status: 503 });
    expect(listener).not.toHaveBeenCalled();
  } finally { unsubscribe(); }
});

test("FormData 요청은 브라우저가 multipart boundary를 설정하도록 Content-Type을 비운다", async () => {
  const { apiRequest } = require("./client");
  const body = new FormData(); body.append("file", new Blob(["상품명\n테스트"]), "offer.csv");
  jest.spyOn(global, "fetch").mockResolvedValue({ ok: true, status: 200, json: async () => ({ data: {} }) });
  await apiRequest("/supplier-offer-drafts/import", { method: "POST", body });
  expect(global.fetch.mock.calls[0][1].headers["Content-Type"]).toBeUndefined();
});
