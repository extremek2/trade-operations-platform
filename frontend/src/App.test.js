import { render, screen } from '@testing-library/react';
import App from './App';

beforeEach(() => { window.scrollTo = jest.fn(); window.history.pushState({}, '', '/'); jest.restoreAllMocks(); });

test('세션이 없으면 로그인 화면을 표시한다', async () => {
  jest.spyOn(global, 'fetch').mockResolvedValue({ ok: false, status: 401, json: async () => ({ success: false }) });
  render(<App />);
  expect((await screen.findAllByRole('button', { name: '로그인' })).length).toBe(2);
  expect(screen.getByRole('button', { name: '화주 회원가입' })).toBeInTheDocument();
});

test('Refresh 세션이 있으면 인증된 조직의 화물을 표시한다', async () => {
  jest.spyOn(global, 'fetch').mockImplementation(async url => {
    if (String(url).includes('/auth/refresh')) return { ok: true, json: async () => ({ success: true, data: { accessToken: 'access-token', user: { sessionKind: 'ORGANIZATION', organizationName: 'ABC Trading', email: 'ops@example.com', role: 'OWNER' } } }) };
    return { ok: true, json: async () => ({ success: true, data: [{ shipmentId:'shipment-id', caseNumber:'IMP-2026-001', priority:'URGENT', status:'OPEN', currentStage:'IN_TRANSIT', transportMode:'SEA', originLocationCode:'CNSHA', destinationLocationCode:'KRPUS' }] }) };
  });
  render(<App />);
  expect(await screen.findByText('IMP-2026-001')).toBeInTheDocument();
  expect(screen.getByText('ABC Trading')).toBeInTheDocument();
  expect(global.fetch).toHaveBeenCalledWith(expect.stringMatching(/\/shipments\?/), expect.objectContaining({ credentials:'include' }));
});

test('상품 후보 화면은 Spring API만 사용한다', async () => {
  window.history.replaceState({}, '', '/products');
  jest.spyOn(global, 'fetch').mockImplementation(async url => ({ ok: true, json: async () => ({ success: true,
    data: String(url).includes('/auth/refresh') ? { accessToken: 'token', user: { sessionKind: 'ORGANIZATION', organizationName: '운영 화주', email: 'owner@example.test', role: 'OWNER' } } : [] }) }));
  render(<App/>);
  expect(await screen.findByRole('heading', { level: 1, name: '상품 후보' })).toBeInTheDocument();
  expect(global.fetch.mock.calls.some(([url]) => String(url).includes('/products'))).toBe(true);
  expect(global.fetch.mock.calls.some(([url]) => String(url).includes('8000'))).toBe(false);
});

test('공급 견적 화면은 상품과 거래처와 견적 원장을 함께 조회한다', async () => {
  window.history.replaceState({}, '', '/sourcing');
  jest.spyOn(global, 'fetch').mockImplementation(async url => ({ ok: true, json: async () => ({ success: true,
    data: String(url).includes('/auth/refresh') ? { accessToken: 'token', user: { sessionKind: 'ORGANIZATION', organizationName: '운영 화주', email: 'owner@example.test', role: 'OWNER' } } : [] }) }));
  render(<App/>);
  expect(await screen.findByRole('heading', { level: 1, name: '공급 견적' })).toBeInTheDocument();
  expect(global.fetch.mock.calls.some(([url]) => String(url).endsWith('/products'))).toBe(true);
  expect(global.fetch.mock.calls.some(([url]) => String(url).endsWith('/quotes'))).toBe(true);
  expect(global.fetch.mock.calls.some(([url]) => String(url).endsWith('/organizations/current/partners'))).toBe(true);
});
