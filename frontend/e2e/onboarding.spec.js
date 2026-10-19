const { test, expect } = require('@playwright/test');
const { randomUUID } = require('crypto');
const API = 'http://127.0.0.1:18080';
const WEB = `http://localhost:${process.env.E2E_WEB_PORT || '3100'}`;
const PASSWORD = 'E2e-password-123!';

async function signup(page, company, email) {
  await page.goto('/');
  await page.getByRole('button', { name: '화주 회원가입' }).click();
  await page.getByLabel('회사명', { exact: true }).fill(company);
  await page.getByLabel('담당자명').fill('신청 담당자');
  await page.getByLabel('이메일', { exact: true }).fill(email);
  await page.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: '가입하고 조직 개설 신청' }).click();
  await expect(page).toHaveURL(/\/application$/);
  await expect(page.getByRole('heading', { name: '이메일 확인이 필요합니다' })).toBeVisible();
}
async function verify(page, request, email) {
  const response = await request.post(`${API}/_e2e/mail?email=${encodeURIComponent(email)}`);
  expect(response.ok()).toBeTruthy();
  const message = await response.json();
  const link = message.body.split('\n').at(-1);
  await page.goto(link);
  await expect(page).toHaveURL(`${WEB}/verify-email`);
  await page.getByRole('button', { name: '이메일 확인 완료하기' }).click();
  await expect(page.getByText('이메일 확인이 완료되었습니다.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '신청 상태 확인' }).click();
  await expect(page.getByText('이메일 확인이 완료되었습니다. 관리자가 회사 정보를 검토하고 있습니다.')).toBeVisible();
  return link;
}
async function adminLogin(browser) {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${WEB}/system-admin/login`);
  await page.getByLabel('이메일', { exact: true }).fill('admin@example.test');
  await page.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: '관리자 로그인', exact: true }).click();
  await expect(page.getByRole('heading', { name: '조직 개설 신청 검토' })).toBeVisible();
  return { context, page };
}

test.beforeEach(async ({ request }) => {
  await expect.poll(async () => (await (await request.get(`${API}/_e2e/ready`)).json()).ready).toBe(true);
});

test('가입 → 이메일 확인 → 관리자 승인 → 화주 작업 공간, 권한 우회 차단', async ({ page, browser, request }) => {
  const company = `승인 검증 ${randomUUID().slice(0, 8)}`;
  const email = `${randomUUID()}@example.test`;
  await signup(page, company, email);
  const session = await (await page.request.post(`http://localhost:18080/api/v1/auth/refresh`)).json();
  const bearer = { Authorization: `Bearer ${session.data.accessToken}` };
  expect((await page.request.get(`${API}/api/v1/shipments`, { headers: bearer })).status()).toBe(403);
  expect((await page.request.get(`${API}/api/v1/system-admin/applications`, { headers: bearer })).status()).toBe(403);
  const link = await verify(page, request, email);
  const admin = await adminLogin(browser);
  try {
    await admin.page.getByRole('button', { name: `${company} 신청 상세 보기` }).click();
    await expect(admin.page.getByRole('button', { name: '승인 검토' })).toBeDisabled();
    await admin.page.getByLabel('신청자에게 전달할 사유').fill('회사 정보 확인 완료');
    await admin.page.getByLabel('관리자 내부 메모 (선택)').fill('이 메모는 관리자만 확인');
    await admin.page.getByRole('button', { name: '승인 검토' }).click();
    await expect(admin.page.getByRole('dialog')).toContainText(email);
    await admin.page.getByRole('button', { name: '최종 승인' }).click();
    await expect(admin.page.getByText('조직 개설을 승인했습니다.', { exact: true })).toBeVisible();
    await expect(admin.page.getByRole('button', { name: '최종 승인' })).toHaveCount(0);
    await admin.page.screenshot({ path: 'test-results/admin-approved.png', fullPage: true });
    await page.getByRole('button', { name: '상태 새로고침' }).click();
    await expect(page.getByRole('button', { name: '작업 공간으로 이동' })).toBeVisible();
    await expect(page.getByText('이 메모는 관리자만 확인')).toHaveCount(0);
    await page.getByRole('button', { name: '작업 공간으로 이동' }).click();
    await expect(page).toHaveURL(`${WEB}/`);
    await expect(page.getByRole('button', { name: '화물 현황', exact: true })).toBeVisible();
    await expect(page.locator('.workspace-card')).toContainText(company);
    await page.goto('/system-admin/applications');
    await expect(page.getByRole('heading', { name: '접근 권한이 없습니다' })).toBeVisible();
    await page.goto(link);
    await page.getByRole('button', { name: '이메일 확인 완료하기' }).click();
    await expect(page.getByRole('alert')).toContainText('유효하지 않거나 만료된');
  } finally { await admin.context.close(); }
});

test('반려 사유 확인 → 보완 재신청, 모바일 화면과 이력 보존', async ({ page, browser, request }) => {
  const company = `반려 검증 ${randomUUID().slice(0, 8)}`;
  const email = `${randomUUID()}@example.test`;
  await signup(page, company, email);
  await verify(page, request, email);
  const admin = await adminLogin(browser);
  try {
    await admin.page.getByRole('button', { name: `${company} 신청 상세 보기` }).click();
    await admin.page.getByLabel('신청자에게 전달할 사유').fill('사업자번호를 보완해 주세요.');
    await admin.page.getByLabel('관리자 내부 메모 (선택)').fill('신청자에게 보이지 않는 메모');
    await admin.page.getByRole('button', { name: '반려 검토' }).click();
    await admin.page.getByRole('button', { name: '최종 반려' }).click();
    await expect(admin.page.getByText('신청을 반려했습니다.', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '상태 새로고침' }).click();
    await expect(page.getByRole('heading', { name: '보완이 필요한 내용' })).toBeVisible();
    await expect(page.getByText('신청자에게 보이지 않는 메모')).toHaveCount(0);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: 'test-results/applicant-rejected-mobile.png', fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByLabel('회사명', { exact: true }).fill(`${company} 보완`);
    await page.getByLabel('사업자번호 (선택)').fill('TEST-001');
    await page.getByRole('button', { name: '보완 내용으로 재신청' }).click();
    await expect(page.getByText('이메일 확인이 완료되었습니다. 관리자가 회사 정보를 검토하고 있습니다.')).toBeVisible();
    await expect(page.locator('.history-list li')).toHaveCount(2);
    await expect(page.locator('.history-list')).toContainText('사업자번호를 보완해 주세요.');
    await admin.page.getByRole('button', { name: '← 신청 목록으로' }).click();
    await expect(admin.page.getByRole('button', { name: `${company} 보완 신청 상세 보기` })).toBeVisible();
  } finally { await admin.context.close(); }
});

test('OWNER 직원 등록 → ADMIN 지정·해제 → 비활성화와 기존 세션 차단', async ({ page, browser, request }) => {
  const company = `직원 관리 ${randomUUID().slice(0, 8)}`;
  const ownerEmail = `${randomUUID()}@example.test`;
  const employeeEmail = `${randomUUID()}@example.test`;
  await signup(page, company, ownerEmail);
  await verify(page, request, ownerEmail);
  const admin = await adminLogin(browser);
  const employeeContext = await browser.newContext();
  const employeePage = await employeeContext.newPage();
  try {
    await admin.page.getByRole('button', { name: `${company} 신청 상세 보기` }).click();
    await admin.page.getByLabel('신청자에게 전달할 사유').fill('회사 정보 확인 완료');
    await admin.page.getByRole('button', { name: '승인 검토' }).click();
    await admin.page.getByRole('button', { name: '최종 승인' }).click();
    await expect(admin.page.getByText('조직 개설을 승인했습니다.', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '상태 새로고침' }).click();
    await page.getByRole('button', { name: '작업 공간으로 이동' }).click();
    await page.getByRole('button', { name: '조직 직원 관리', exact: true }).click();
    await signup(employeePage, '직원 계정 확인', employeeEmail);
    await verify(employeePage, request, employeeEmail);
    await page.getByLabel('직원 이메일').fill(employeeEmail);
    await page.getByLabel('변경 사유').fill('내부 물류 직원 등록');
    await page.getByRole('button', { name: '내용 확인' }).click();
    await page.getByRole('button', { name: '최종 확인' }).click();
    await expect(page.getByRole('status')).toContainText('직원을 추가했습니다');
    await employeePage.getByRole('button', { name: '로그아웃' }).click();
    await employeePage.getByLabel('이메일', { exact: true }).fill(employeeEmail);
    await employeePage.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
    await employeePage.getByRole('button', { name: '로그인', exact: true }).last().click();
    await expect(employeePage.locator('.workspace-card')).toContainText('OPERATOR');
    await employeePage.goto('/organization/members');
    await expect(employeePage.getByRole('heading', { name: '접근 권한이 없습니다' })).toBeVisible();
    async function update(role, status = 'ACTIVE') {
      await page.getByRole('button', { name: `${employeeEmail} 권한 변경` }).click();
      await page.getByLabel('직원 권한').selectOption(role);
      await page.getByLabel('직원 상태').selectOption(status);
      await page.getByLabel('변경 사유').fill('담당 업무 변경');
      await page.getByRole('button', { name: '내용 확인' }).click();
      await expect(page.getByRole('dialog')).toContainText(employeeEmail);
      await page.getByRole('button', { name: '최종 확인' }).click();
      await expect(page.getByRole('status')).toContainText('직원 권한과 상태를 변경했습니다');
      await expect(page.getByRole('heading', { name: '내부 직원 추가' })).toBeVisible();
    }
    await update('ADMIN');
    await employeePage.reload();
    await expect(employeePage.getByRole('heading', { name: '직원 목록' })).toBeVisible();
    await expect(employeePage.getByLabel('직원 권한').locator('option')).toHaveText(['OPERATOR', 'VIEWER']);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: 'test-results/organization-members-mobile.png', fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await update('VIEWER');
    await employeePage.getByRole('button', { name: '목록 새로고침' }).click();
    await expect(employeePage.getByRole('heading', { name: '접근 권한이 없습니다' })).toBeVisible();
    await update('VIEWER', 'INACTIVE');
    await employeePage.reload();
    await expect(employeePage.getByRole('button', { name: '화주 회원가입' })).toBeVisible();
  } finally { await admin.context.close(); await employeeContext.close(); }
});

test('외부 업체별 3명 초대 → 이메일 건별 접속 → 문서번호 등록 → 철회', async ({ page, browser, request }) => {
  const company = `협업 화주 ${randomUUID().slice(0, 8)}`;
  const email = `${randomUUID()}@example.test`;
  const externalEmail = `${randomUUID()}@example.test`;
  await signup(page, company, email); await verify(page, request, email);
  const admin = await adminLogin(browser);
  const externalContext = await browser.newContext(); const externalPage = await externalContext.newPage();
  try {
    await admin.page.getByRole('button', { name: `${company} 신청 상세 보기` }).click();
    await admin.page.getByLabel('신청자에게 전달할 사유').fill('회사 정보 확인');
    await admin.page.getByRole('button', { name: '승인 검토' }).click();
    await admin.page.getByRole('button', { name: '최종 승인' }).click();
    await expect(admin.page.getByText('조직 개설을 승인했습니다.', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '상태 새로고침' }).click();
    await page.getByRole('button', { name: '작업 공간으로 이동' }).click();
    await expect(page).toHaveURL(`${WEB}/`);
    const session = await (await page.request.post('http://localhost:18080/api/v1/auth/refresh')).json();
    const response = await page.request.post(`${API}/api/v1/shipments`, { headers: { Authorization: `Bearer ${session.data.accessToken}` }, data: { caseNumber: `CASE-${randomUUID()}`, direction: 'IMPORT', transportMode: 'SEA' } });
    expect(response.ok()).toBeTruthy(); const shipment = (await response.json()).data;
    await page.goto(`/shipments/${shipment.shipmentId}`);
    await page.getByLabel('업체명', { exact: true }).fill('협력 포워더');
    await page.getByRole('button', { name: '업체 등록', exact: true }).click();
    await page.getByRole('button', { name: '이 건에 업체 연결' }).click();
    await expect(page.getByRole('heading', { name: '협력 포워더 · 포워더' })).toBeVisible();
    for (let i = 0; i < 3; i++) {
      await page.getByRole('button', { name: '협력 포워더 담당자 초대' }).click();
      await page.getByLabel('담당자 이름').fill(`외부 담당자 ${i+1}`);
      await page.getByLabel('담당자 이메일').fill(i === 0 ? externalEmail : `${randomUUID()}@example.test`);
      await page.getByLabel('참여 권한').selectOption(i === 0 ? 'CONTRIBUTOR' : 'VIEWER');
      await page.getByRole('button', { name: '초대 내용 확인' }).click();
      await page.getByRole('button', { name: '최종 확인' }).click();
      await expect(page.getByRole('status')).toContainText('초대를 등록했습니다');
      await expect(page.getByText(`${i+1} / 3명`, { exact: true })).toBeVisible();
    }
    await expect(page.getByRole('button', { name: '협력 포워더 담당자 초대' })).toBeDisabled();
    const mail = await (await request.post(`${API}/_e2e/mail?email=${encodeURIComponent(externalEmail)}`)).json();
    const link = mail.body.split('\n').at(-1);
    await externalPage.goto(link);
    await expect(externalPage).toHaveURL(`${WEB}/external-access`);
    await externalPage.getByRole('button', { name: '초대 확인하고 건에 접속' }).click();
    await expect(externalPage.getByRole('heading', { name: shipment.caseNumber })).toBeVisible();
    await externalPage.getByLabel('문서번호', { exact: true }).fill('HBL-EXTERNAL-001');
    await externalPage.getByRole('button', { name: '문서번호 등록', exact: true }).click();
    await expect(externalPage.getByText('MBL · HBL-EXTERNAL-001', { exact: true })).toBeVisible();
    await externalPage.setViewportSize({ width: 390, height: 844 });
    await externalPage.screenshot({ path: 'test-results/external-case-mobile.png', fullPage: true });
    expect(await externalPage.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    const extSession = await (await externalPage.request.post('http://localhost:18080/api/v1/auth/refresh')).json();
    const headers = { Authorization: `Bearer ${extSession.data.accessToken}` };
    expect((await externalPage.request.get(`${API}/api/v1/shipments`, { headers })).status()).toBe(403);
    expect((await externalPage.request.get(`${API}/api/v1/case-workspace/${randomUUID()}`, { headers })).status()).toBe(404);
    await page.getByRole('button', { name: '참여자 새로고침' }).click();
    await expect(page.getByText('참여 중 · 조회 및 문서번호 등록')).toBeVisible();
    await page.getByRole('button', { name: `${externalEmail} 참여 철회` }).click();
    await page.getByLabel('철회 사유').fill('담당 업무 종료');
    await page.getByRole('button', { name: '최종 확인' }).click();
    await expect(page.getByRole('status')).toContainText('참여 권한을 철회했습니다');
    await expect(page.getByText('2 / 3명', { exact: true })).toBeVisible();
    await externalPage.getByRole('button', { name: '건 새로고침' }).click();
    await expect(externalPage.getByRole('heading', { name: '건별 업무 참여' })).toBeVisible();
    await externalPage.goto(link);
    await externalPage.getByRole('button', { name: '초대 확인하고 건에 접속' }).click();
    await expect(externalPage.getByRole('alert')).toContainText('유효하지 않거나 만료된');
  } finally { await admin.context.close(); await externalContext.close(); }
});
