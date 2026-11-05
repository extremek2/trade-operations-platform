import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import { applySession } from './api/client';

const account = { userId: 'user-1', name: '신청자', email: 'applicant@example.test', sessionKind: 'ACCOUNT', emailVerified: false };
const admin = { ...account, sessionKind: 'PLATFORM', systemAdmin: true };
const application = { applicationId: 'application-1', organizationName: '테스트 회사', applicantName: '신청자', applicantEmail: account.email, status: 'PENDING_EMAIL', version: 0, submittedAt: '2026-09-08T12:00:00' };
const ok = data => ({ ok: true, status: 200, json: async () => ({ success: true, data }) });
const fail = (status, message) => ({ ok: false, status, json: async () => ({ success: false, message }) });
function mockSession(user = account, item = application, custom = () => undefined) {
  return jest.spyOn(global, 'fetch').mockImplementation(async (url, options = {}) => {
    const path = new URL(url).pathname;
    const result = custom(path, options, url);
    if (result !== undefined) return result;
    if (path.endsWith('/auth/refresh')) return user ? ok({ accessToken: 'token', user }) : fail(401, '세션 없음');
    if (path.endsWith('/auth/me')) return ok(user);
    if (path.endsWith('/me/applications')) return ok({ content: item ? [item] : [], totalPages: 1, totalElements: item ? 1 : 0 });
    throw new Error(`Unexpected API: ${path}`);
  });
}
beforeEach(() => { jest.restoreAllMocks(); applySession(null); window.scrollTo = jest.fn(); window.history.replaceState({}, '', '/'); });

test('승인 대기 계정은 화물 URL에서도 신청 화면으로 이동하고 화물 API를 호출하지 않는다', async () => {
  window.history.replaceState({}, '', '/shipments/new');
  const fetch = mockSession(); render(<App/>);
  expect(await screen.findByRole('heading', { name: '이메일 확인이 필요합니다' })).toBeInTheDocument();
  expect(window.location.pathname).toBe('/application');
  expect(fetch.mock.calls.some(([url]) => String(url).includes('/shipments'))).toBe(false);
});

test('일반 계정이 관리자 URL을 열어도 관리자 API에 접근하지 않는다', async () => {
  window.history.replaceState({}, '', '/system-admin/applications');
  const fetch = mockSession(); render(<App/>);
  expect(await screen.findByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument();
  expect(fetch.mock.calls.some(([url]) => String(url).includes('/system-admin/applications'))).toBe(false);
});

test('세션 복원 네트워크 실패는 재시도할 수 있다', async () => {
  jest.spyOn(global, 'fetch').mockRejectedValueOnce(new Error('offline')).mockResolvedValue(fail(401, '세션 없음'));
  render(<App/>);
  fireEvent.click(await screen.findByRole('button', { name: '다시 연결' }));
  expect(await screen.findByRole('button', { name: '화주 회원가입' })).toBeInTheDocument();
});

test('이메일 링크는 주소에서 제거하고 사용자가 확인하기 전에는 소비하지 않는다', async () => {
  const token = 'A'.repeat(43); window.history.replaceState({}, '', `/verify-email#token=${token}`);
  const fetch = mockSession(null, null, path => path.endsWith('/confirm') ? ok(null) : undefined);
  render(<App/>);
  expect(window.location.hash).toBe('');
  expect(fetch.mock.calls.some(([url]) => String(url).endsWith('/confirm'))).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: '이메일 확인 완료하기' }));
  expect(await screen.findByText('이메일 확인이 완료되었습니다.')).toBeInTheDocument();
  const [, request] = fetch.mock.calls.find(([url]) => String(url).endsWith('/confirm'));
  expect(request.credentials).toBe('omit'); expect(request.headers.Authorization).toBeUndefined();
  expect(JSON.parse(request.body).token).toBe(token);
});

test('유효하지 않은 이메일 링크는 확인 버튼을 표시하지 않는다', async () => {
  window.history.replaceState({}, '', '/verify-email#token=invalid'); mockSession(null); await act(async () => { render(<App/>); });
  expect(screen.queryByRole('button', { name: '이메일 확인 완료하기' })).not.toBeInTheDocument();
  expect(screen.getByText(/확인 링크가 없거나 사용할 수 없습니다/)).toBeInTheDocument();
});

test('이메일 재요청 제한을 받으면 안내하고 재요청 버튼을 잠근다', async () => {
  mockSession(account, application, path => path.endsWith('/email-verifications/request') ? fail(429, '잠시 후 다시 요청해 주세요.') : undefined);
  render(<App/>); fireEvent.click(await screen.findByRole('button', { name: '확인 메일 다시 요청' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 요청');
  expect(screen.getByRole('button', { name: /초 후 재요청 가능/ })).toBeDisabled();
});

test('반려 후 재신청은 이전 신청 ID를 포함하며 새 검토 상태로 갱신한다', async () => {
  let item = { ...application, status: 'REJECTED', reason: '회사명 보완 필요' };
  const fetch = mockSession({ ...account, emailVerified: true }, item, path => {
    if (path.endsWith('/me/applications')) return ok({ content: [item], totalPages: 1 });
    if (path.endsWith('/organization-applications')) { item = { ...item, applicationId: 'application-2', status: 'PENDING_REVIEW' }; return ok(item); }
    return undefined;
  });
  render(<App/>);
  fireEvent.change(await screen.findByLabelText('회사명'), { target: { value: '보완 회사' } });
  fireEvent.click(screen.getByRole('button', { name: '보완 내용으로 재신청' }));
  expect(await screen.findByText('이메일 확인이 완료되었습니다. 관리자가 회사 정보를 검토하고 있습니다.')).toBeInTheDocument();
  const [, request] = fetch.mock.calls.find(([url]) => String(url).endsWith('/organization-applications'));
  expect(JSON.parse(request.body)).toEqual(expect.objectContaining({ organizationName: '보완 회사', previousApplicationId: 'application-1' }));
});

test('관리자 승인 충돌 시 최신 상태를 불러오고 결정 버튼을 제거한다', async () => {
  window.history.replaceState({}, '', '/system-admin/applications/application-1'); let reads = 0;
  const fetch = mockSession(admin, null, path => {
    if (path.endsWith('/approve')) return fail(409, '이미 처리됨');
    if (path.endsWith('/system-admin/applications/application-1')) return ok({ application: { ...application, status: ++reads === 1 ? 'PENDING_REVIEW' : 'APPROVED', version: reads, reason: '다른 관리자가 승인' }, internalNote: '내부 메모' });
    return undefined;
  });
  render(<App/>); fireEvent.change(await screen.findByLabelText('신청자에게 전달할 사유'), { target: { value: '확인 완료' } });
  fireEvent.click(screen.getByRole('button', { name: '승인 검토' }));
  expect(fetch.mock.calls.some(([url]) => String(url).endsWith('/approve'))).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: '최종 승인' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('최신 신청을 불러왔습니다');
  expect(screen.queryByRole('button', { name: '승인 검토' })).not.toBeInTheDocument();
  expect(screen.getByText('다른 관리자가 승인')).toBeInTheDocument();
});

test('관리자 결정 요청이 진행 중이면 반복 클릭해도 한 번만 전송한다', async () => {
  window.history.replaceState({}, '', '/system-admin/applications/application-1'); let finish;
  const fetch = mockSession(admin, null, path => {
    if (path.endsWith('/approve')) return new Promise(resolve => { finish = resolve; });
    if (path.endsWith('/system-admin/applications/application-1')) return ok({ application: { ...application, status: 'PENDING_REVIEW' } });
    return undefined;
  });
  render(<App/>); fireEvent.change(await screen.findByLabelText('신청자에게 전달할 사유'), { target: { value: '승인' } });
  fireEvent.click(screen.getByRole('button', { name: '승인 검토' }));
  const button = screen.getByRole('button', { name: '최종 승인' }); fireEvent.click(button); fireEvent.click(button);
  await waitFor(() => expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/approve'))).toHaveLength(1));
  finish(ok({ application: { ...application, status: 'APPROVED', reason: '승인' } }));
  expect(await screen.findByText('조직 개설을 승인했습니다.')).toBeInTheDocument();
});

test('이메일 미확인 신청은 관리자가 승인할 수 없다', async () => {
  window.history.replaceState({}, '', '/system-admin/applications/application-1');
  mockSession(admin, null, path => path.endsWith('/system-admin/applications/application-1') ? ok({ application }) : undefined);
  render(<App/>);
  expect(await screen.findByText('신청자가 이메일 확인을 완료해야 승인 또는 반려할 수 있습니다.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '승인 검토' })).not.toBeInTheDocument();
});
