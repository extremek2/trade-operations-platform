import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';
import { applySession } from './api/client';
const owner = { userId: 'owner', organizationId: 'org', organizationName: '화주', email: 'owner@example.test', sessionKind: 'ORGANIZATION', role: 'OWNER' };
const member = { userId: 'employee', name: '직원', email: 'employee@example.test', role: 'OPERATOR', status: 'ACTIVE', version: 4 };
const ok = data => ({ ok: true, status: 200, json: async () => ({ success: true, data }) });
const fail = (status, message) => ({ ok: false, status, json: async () => ({ success: false, message }) });
function setup(user = owner, custom = () => undefined) {
  return jest.spyOn(global, 'fetch').mockImplementation(async (url, options = {}) => {
    const path = new URL(url).pathname;
    const result = custom(path, options);
    if (result !== undefined) return result;
    if (path.endsWith('/auth/refresh')) return ok({ accessToken: 'token', user });
    if (path.endsWith('/auth/me')) return ok(user);
    if (path.endsWith('/members')) return ok({ content: [{ ...owner, status: 'ACTIVE', version: 0 }, member], totalPages: 1 });
    throw new Error(`Unexpected API ${path}`);
  });
}
beforeEach(() => { jest.restoreAllMocks(); applySession(null); window.scrollTo = jest.fn(); window.history.replaceState({}, '', '/organization/members'); });
async function edit() {
  fireEvent.click(await screen.findByRole('button', { name: 'employee@example.test 권한 변경' }));
  fireEvent.change(screen.getByLabelText('직원 권한'), { target: { value: 'VIEWER' } });
  fireEvent.change(screen.getByLabelText('변경 사유'), { target: { value: '조회 업무로 변경' } });
  fireEvent.click(screen.getByRole('button', { name: '내용 확인' }));
}

test('OWNER는 본인을 변경할 수 없고 직원 ADMIN 지정 선택지가 있다', async () => {
  setup(); render(<App/>);
  expect(await screen.findByRole('heading', { name: '직원 목록' })).toBeInTheDocument();
  expect(await screen.findByLabelText('직원 권한')).toContainHTML('ADMIN');
  expect(screen.queryByRole('button', { name: 'owner@example.test 권한 변경' })).not.toBeInTheDocument();
});

test('ADMIN은 다른 ADMIN을 변경하거나 ADMIN 역할을 지정할 수 없다', async () => {
  setup({ ...owner, role: 'ADMIN' }, path => path.endsWith('/members') ? ok({ content: [{ ...member, role: 'ADMIN' }], totalPages: 1 }) : undefined);
  render(<App/>);
  expect(await screen.findByLabelText('직원 권한')).not.toContainHTML('ADMIN');
  expect(screen.queryByRole('button', { name: 'employee@example.test 권한 변경' })).not.toBeInTheDocument();
});

test('VIEWER가 직원 관리 URL에 접근해도 직원 API는 호출하지 않는다', async () => {
  const fetch = setup({ ...owner, role: 'VIEWER' }); render(<App/>);
  expect(await screen.findByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument();
  expect(fetch.mock.calls.some(([url]) => String(url).includes('/members'))).toBe(false);
});

test('변경 대상을 확인한 뒤 버전과 사유를 전송하고 중복 클릭을 차단한다', async () => {
  let resolve;
  const fetch = setup(owner, (path, options) => options.method === 'PATCH' ? new Promise(r => { resolve = r; }) : undefined);
  render(<App/>); await edit();
  expect(within(screen.getByRole('dialog')).getByText(member.email)).toBeInTheDocument();
  expect(fetch.mock.calls.some(([, options]) => options?.method === 'PATCH')).toBe(false);
  const confirm = screen.getByRole('button', { name: '최종 확인' });
  fireEvent.click(confirm); fireEvent.click(confirm);
  expect(fetch.mock.calls.filter(([, options]) => options?.method === 'PATCH')).toHaveLength(1);
  const [, request] = fetch.mock.calls.find(([, options]) => options?.method === 'PATCH');
  expect(JSON.parse(request.body)).toEqual({ role: 'VIEWER', status: 'ACTIVE', version: 4, reason: '조회 업무로 변경' });
  await act(async () => resolve(ok({ ...member, role: 'VIEWER', version: 5 })));
  expect(await screen.findByRole('status')).toHaveTextContent('변경했습니다');
});

test('409 충돌 시 편집을 닫고 최신 목록을 다시 읽는다', async () => {
  let reads = 0;
  setup(owner, (path, options) => {
    if (options.method === 'PATCH') return fail(409, '직원 정보가 변경되었습니다.');
    if (path.endsWith('/members')) return ok({ content: [{ ...member, role: ++reads === 1 ? 'OPERATOR' : 'ADMIN' }], totalPages: 1 });
    return undefined;
  });
  render(<App/>); await edit(); fireEvent.click(screen.getByRole('button', { name: '최종 확인' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('직원 정보가 변경');
  await waitFor(() => expect(reads).toBe(2));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '내부 직원 추가' })).toBeInTheDocument();
});

test('직원 등록은 확인한 이메일과 제한된 역할로 전송한다', async () => {
  const fetch = setup(owner, (path, options) => path.endsWith('/members') && options.method === 'POST' ? ok(member) : undefined);
  render(<App/>);
  fireEvent.change(await screen.findByLabelText('직원 이메일'), { target: { value: member.email } });
  fireEvent.change(screen.getByLabelText('변경 사유'), { target: { value: '물류 담당 직원 등록' } });
  fireEvent.click(screen.getByRole('button', { name: '내용 확인' }));
  fireEvent.click(screen.getByRole('button', { name: '최종 확인' }));
  expect(await screen.findByRole('status')).toHaveTextContent('직원을 추가했습니다');
  const [, request] = fetch.mock.calls.find(([url, options]) => String(url).endsWith('/members') && options?.method === 'POST');
  expect(JSON.parse(request.body)).toEqual({ email: member.email, role: 'OPERATOR', reason: '물류 담당 직원 등록' });
});
