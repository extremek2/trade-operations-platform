import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import CasePartners from './components/CasePartners';
import { applySession } from './api/client';
const external = { userId: 'external', name: '외부 담당자', email: 'external@example.test', sessionKind: 'CASE', shipmentId: 'case-1', participantId: 'p-1', caseAccessLevel: 'VIEWER' };
const ok = data => ({ ok: true, status: 200, json: async () => ({ success: true, data }) });
const fail = (status, message) => ({ ok: false, status, json: async () => ({ success: false, message }) });
const partner = { casePartnerId: 'partner-1', businessPartnerId: 'business-partner-1', name: '테스트 포워더', role: 'FORWARDER', occupied: 0, participants: [] };
const workspace = { caseNumber: 'CASE-001', documents: [], accessLevel: 'VIEWER', transportMode: 'SEA' };
function mock(user = external, custom = () => undefined) {
  return jest.spyOn(global, 'fetch').mockImplementation(async (url, options = {}) => {
    const path = new URL(url).pathname; const result = custom(path, options);
    if (result !== undefined) return result;
    if (path.endsWith('/auth/refresh')) return user ? ok({ accessToken: 'token', user }) : fail(401, '세션 없음');
    if (path.endsWith('/case-workspace/case-1')) return ok(workspace);
    if (path.endsWith('/organizations/current/partners')) return ok([]);
    if (path.endsWith('/partners')) return ok([partner]);
    throw new Error(`Unexpected API ${path}`);
  });
}
beforeEach(() => { jest.restoreAllMocks(); applySession(null); window.scrollTo = jest.fn(); window.history.replaceState({}, '', '/external-case'); });

test('외부 세션으로 화물 목록 주소를 열어도 참여 건으로 이동하고 화주 API는 조회하지 않는다', async () => {
  window.history.replaceState({}, '', '/'); const fetch = mock(); render(<App/>);
  expect(await screen.findByRole('heading', { name: 'CASE-001' })).toBeInTheDocument();
  expect(window.location.pathname).toBe('/external-case');
  expect(fetch.mock.calls.some(([url]) => String(url).includes('/shipments'))).toBe(false);
  expect(screen.queryByLabelText('문서번호')).not.toBeInTheDocument();
});

test('외부 링크는 주소에서 제거하며 명시적 클릭 전에는 접속하지 않는다', async () => {
  const token = 'a'.repeat(43);
  window.history.replaceState({}, '', `/external-access#invitation=11111111-1111-1111-1111-111111111111&token=${token}`);
  const fetch = mock(null, path => path.endsWith('/case-links/confirm') ? ok({ accessToken: 'external-token', user: external }) : undefined);
  render(<App/>);
  expect(window.location.hash).toBe('');
  expect(fetch.mock.calls.some(([url]) => String(url).includes('/case-links/confirm'))).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: '초대 확인하고 건에 접속' }));
  expect(await screen.findByRole('heading', { name: 'CASE-001' })).toBeInTheDocument();
  const [, request] = fetch.mock.calls.find(([url]) => String(url).endsWith('/case-links/confirm'));
  expect(request.headers.Authorization).toBeUndefined(); expect(request.credentials).toBe('include');
  expect(JSON.parse(request.body)).toEqual({ token });
});

test('만료 링크 확인 실패 후 초대 이메일로 새 링크를 요청할 수 있다', async () => {
  window.history.replaceState({}, '', `/external-access#invitation=11111111-1111-1111-1111-111111111111&token=${'b'.repeat(43)}`);
  const fetch = mock(null, path => path.endsWith('/case-links/confirm') ? fail(400, '만료된 접속 링크') : path.endsWith('/case-links/request') ? ok(null) : undefined);
  render(<App/>); fireEvent.click(screen.getByRole('button', { name: '초대 확인하고 건에 접속' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('만료');
  fireEvent.change(screen.getByLabelText('초대받은 이메일'), { target: { value: external.email } });
  fireEvent.click(screen.getByRole('button', { name: '접속 링크 요청' }));
  expect(await screen.findByRole('status')).toHaveTextContent('유효한 초대');
  expect(screen.getByRole('button', { name: /초 후 재요청 가능/ })).toBeDisabled();
  const [, request] = fetch.mock.calls.find(([url]) => String(url).endsWith('/case-links/request'));
  expect(request.credentials).toBe('omit'); expect(request.headers.Authorization).toBeUndefined();
});

test('외부 세션 철회로 복원이 실패하면 이메일 접속 안내로 이동한다', async () => {
  mock(null); render(<App/>);
  expect(await screen.findByRole('heading', { name: '건별 업무 참여' })).toBeInTheDocument();
  expect(window.location.pathname).toBe('/external-access');
});

test('업체별 세 명이면 추가 초대 버튼을 비활성화한다', async () => {
  mock(null, path => path.includes('/shipments/') ? ok([{ ...partner, occupied: 3 }]) : undefined);
  render(<CasePartners shipmentId="case-1"/>);
  expect(await screen.findByRole('button', { name: '테스트 포워더 담당자 초대' })).toBeDisabled();
});

test('이메일과 권한을 최종 확인한 뒤 초대를 한 번만 전송한다', async () => {
  let resolve;
  const fetch = mock(null, path => path.endsWith('/invitations') ? new Promise(r => { resolve = r; }) : undefined);
  render(<CasePartners shipmentId="case-1"/>);
  fireEvent.click(await screen.findByRole('button', { name: '테스트 포워더 담당자 초대' }));
  fireEvent.change(screen.getByLabelText('담당자 이름'), { target: { value: '담당자' } });
  fireEvent.change(screen.getByLabelText('담당자 이메일'), { target: { value: external.email } });
  fireEvent.change(screen.getByLabelText('참여 권한'), { target: { value: 'CONTRIBUTOR' } });
  fireEvent.click(screen.getByRole('button', { name: '초대 내용 확인' }));
  expect(screen.getByRole('dialog')).toHaveTextContent(external.email);
  expect(fetch.mock.calls.some(([url]) => String(url).endsWith('/invitations'))).toBe(false);
  const confirm = screen.getByRole('button', { name: '최종 확인' }); fireEvent.click(confirm); fireEvent.click(confirm);
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/invitations'))).toHaveLength(1);
  await act(async () => resolve(ok({ invitationId: 'invite-1' })));
  expect(await screen.findByRole('status')).toHaveTextContent('초대를 등록했습니다');
});

test('철회는 사유와 대상 확인 후 서버로 전송한다', async () => {
  const person = { participantId: 'p-1', name: '담당자', email: external.email, status: 'ACTIVE', accessLevel: 'VIEWER' };
  const fetch = mock(null, path => path.endsWith('/revoke') ? ok(null) : path.includes('/shipments/') ? ok([{ ...partner, occupied: 1, participants: [person] }]) : undefined);
  render(<CasePartners shipmentId="case-1"/>);
  fireEvent.click(await screen.findByRole('button', { name: `${external.email} 참여 철회` }));
  expect(screen.getByRole('button', { name: '최종 확인' })).toBeDisabled();
  fireEvent.change(screen.getByLabelText('철회 사유'), { target: { value: '담당 업무 종료' } });
  fireEvent.click(screen.getByRole('button', { name: '최종 확인' }));
  expect(await screen.findByRole('status')).toHaveTextContent('철회했습니다');
  const [, request] = fetch.mock.calls.find(([url]) => String(url).endsWith('/revoke'));
  expect(JSON.parse(request.body)).toEqual({ reason: '담당 업무 종료' });
});

test('CONTRIBUTOR 문서 등록은 대표 문서 지정 없이 전송한다', async () => {
  const fetch = mock({ ...external, caseAccessLevel: 'CONTRIBUTOR' }, path => path.endsWith('/documents') ? ok({}) : path.endsWith('/case-workspace/case-1') ? ok({ ...workspace, accessLevel: 'CONTRIBUTOR' }) : undefined);
  render(<App/>);
  fireEvent.change(await screen.findByLabelText('문서번호'), { target: { value: 'HBL-001' } });
  fireEvent.click(screen.getByRole('button', { name: '문서번호 등록' }));
  await waitFor(() => expect(fetch.mock.calls.some(([url]) => String(url).endsWith('/documents'))).toBe(true));
  const [, request] = fetch.mock.calls.find(([url]) => String(url).endsWith('/documents'));
  expect(JSON.parse(request.body).primary).toBe(false);
  await screen.findByRole('heading', { name: '운송 문서번호' });
});

test('같은 접속 안내 페이지에서 메일 링크를 다시 열면 새 토큰을 읽고 주소에서 제거한다', async () => {
  window.history.replaceState({}, '', '/external-access'); mock(null);
  await act(async () => { render(<App/>); });
  expect(screen.queryByRole('button', { name: '초대 확인하고 건에 접속' })).not.toBeInTheDocument();
  await act(async () => {
    window.history.replaceState({}, '', `/external-access#invitation=11111111-1111-1111-1111-111111111111&token=${'c'.repeat(43)}`);
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  });
  expect(screen.getByRole('button', { name: '초대 확인하고 건에 접속' })).toBeInTheDocument();
  expect(window.location.hash).toBe('');
});
