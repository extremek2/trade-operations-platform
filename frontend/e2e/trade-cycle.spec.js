const { test, expect } = require('@playwright/test');
const { randomUUID } = require('crypto');

const API = 'http://127.0.0.1:18080';
const WEB = `http://localhost:${process.env.E2E_WEB_PORT || '3100'}`;
const PASSWORD = 'E2e-password-123!';

async function onboardOwner(page, browser, request) {
  const suffix = randomUUID().slice(0, 8);
  const company = `거래 사이클 ${suffix}`;
  const email = `${randomUUID()}@example.test`;

  await page.goto('/');
  await page.getByRole('button', { name: '화주 회원가입' }).click();
  await page.getByLabel('회사명', { exact: true }).fill(company);
  await page.getByLabel('담당자명').fill('거래 담당자');
  await page.getByLabel('이메일', { exact: true }).fill(email);
  await page.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: '가입하고 조직 개설 신청' }).click();
  await expect(page).toHaveURL(/\/application$/);
  await expect(page.getByRole('heading', { name: '이메일 확인이 필요합니다' })).toBeVisible();

  const mailResponse = await request.post(`${API}/_e2e/mail?email=${encodeURIComponent(email)}`);
  expect(mailResponse.ok()).toBeTruthy();
  const mail = await mailResponse.json();
  await page.goto(mail.body.split('\n').at(-1));
  await page.getByRole('button', { name: '이메일 확인 완료하기' }).click();
  await page.getByRole('button', { name: '신청 상태 확인' }).click();

  const adminContext = await browser.newContext();
  const admin = await adminContext.newPage();
  try {
    await admin.goto(`${WEB}/system-admin/login`);
    await admin.getByLabel('이메일', { exact: true }).fill('admin@example.test');
    await admin.getByLabel('비밀번호', { exact: true }).fill(PASSWORD);
    await admin.getByRole('button', { name: '관리자 로그인', exact: true }).click();
    await admin.getByRole('button', { name: `${company} 신청 상세 보기` }).click();
    await admin.getByLabel('신청자에게 전달할 사유').fill('거래 사이클 브라우저 검증');
    await admin.getByRole('button', { name: '승인 검토' }).click();
    await admin.getByRole('button', { name: '최종 승인' }).click();
    await expect(admin.getByText('조직 개설을 승인했습니다.', { exact: true })).toBeVisible();
  } finally {
    await adminContext.close();
  }

  await page.getByRole('button', { name: '상태 새로고침' }).click();
  await page.getByRole('button', { name: '작업 공간으로 이동' }).click();
  await expect(page).toHaveURL(`${WEB}/`);
  return suffix;
}

test.beforeEach(async ({ request }) => {
  await expect.poll(async () => (await (await request.get(`${API}/_e2e/ready`)).json()).ready).toBe(true);
});

test('공급 제안부터 국내 입고·실제 원가·판매 판단까지 완주한다', async ({ page, browser, request }) => {
  test.setTimeout(120000);
  const suffix = await onboardOwner(page, browser, request);
  const supplier = `국내 공급처 ${suffix}`;
  const product = `스테인리스 텀블러 ${suffix}`;

  await page.getByRole('button', { name: '공급 견적', exact: true }).click();
  await page.getByLabel('공급처명').fill(supplier);
  await page.getByRole('button', { name: '공급처 등록', exact: true }).click();
  await expect(page.getByText(supplier, { exact: true }).first()).toBeVisible();

  await page.getByRole('button', { name: '공급 상품 제안', exact: true }).click();
  const offerForm = page.getByRole('form', { name: '수동 공급 제안 등록' });
  await offerForm.getByLabel('공급처').selectOption({ label: supplier });
  await offerForm.getByLabel('통화').fill('KRW');
  await offerForm.getByLabel('받은 원본 내용').fill(`${product} / MOQ 10 / EA / 8000원`);
  await offerForm.getByLabel('원본 상품명 · 한 줄에 한 행').fill(product);
  await offerForm.getByRole('button', { name: '검토 초안 만들기' }).click();

  const offer = page.getByRole('article', { name: `${supplier} 제안 REVIEW_REQUIRED` });
  await offer.getByLabel('확인 상품명').fill(product);
  await offer.getByLabel(/^MOQ/).fill('10');
  await offer.getByLabel('수량 단위').fill('EA');
  await offer.getByLabel(/^단가/).fill('8000');
  await offer.getByLabel(/^원산지/).fill('KR');
  await offer.getByRole('button', { name: '이 행 확인' }).click();
  await offer.getByRole('button', { name: '추출 확인' }).click();

  const confirmedOffer = page.getByRole('article', { name: `${supplier} 제안 CONFIRMED` });
  await confirmedOffer.getByRole('checkbox', { name: '구매 후보로 선택' }).check();
  await confirmedOffer.getByLabel('희망 수량').fill('10');
  await confirmedOffer.getByRole('button', { name: '선택 행을 견적으로 넘기기' }).click();
  await page.getByRole('button', { name: '견적 검토로 이동' }).click();

  const quote = page.getByRole('article', { name: new RegExp(`^${supplier} OFFER-.* 개정 1$`) });
  await quote.getByRole('button', { name: '수신 처리' }).click();
  await quote.getByRole('button', { name: '선택 처리' }).click();

  await page.getByRole('button', { name: '예상 원가', exact: true }).click();
  const costing = page.getByRole('form', { name: '원가 시나리오 입력' });
  await costing.getByLabel('견적 품목').selectOption({ index: 1 });
  await costing.getByLabel('목표 판매가').fill('15000');
  await costing.getByRole('button', { name: '계산·저장' }).click();
  await expect(page.getByRole('cell', { name: `${product} 기준안` })).toBeVisible();

  await page.getByRole('button', { name: '수입 사이클', exact: true }).click();
  const orderForm = page.getByRole('form', { name: '발주 생성' });
  await orderForm.getByRole('checkbox', { name: new RegExp(product) }).check();
  await orderForm.getByLabel('발주번호').fill(`PO-${suffix}`);
  await orderForm.getByLabel('매입 경로').selectOption('DOMESTIC_STOCK');
  await orderForm.getByRole('button', { name: '발주 초안 생성' }).click();
  await page.getByRole('button', { name: '발주 승인' }).click();

  const externalOrder = page.getByRole('form', { name: '외부 주문 기록' });
  await externalOrder.getByLabel('원 사이트 주문번호').fill(`EXT-${suffix}`);
  await externalOrder.getByLabel(/확정 주문금액/).fill('80000');
  await externalOrder.getByLabel('원 사이트 주문일').fill('2026-10-19');
  await externalOrder.getByRole('button', { name: '외부 주문번호 기록' }).click();

  const receipt = page.getByRole('form', { name: '국내 입고 처리' });
  await receipt.getByLabel('발주 품목').selectOption({ index: 1 });
  await receipt.getByLabel('입고번호').fill(`RCV-${suffix}`);
  await receipt.getByLabel('입고수량').fill('10');
  await receipt.getByLabel('로트번호').fill(`LOT-${suffix}`);
  await receipt.getByRole('button', { name: '국내 입고 확정' }).click();

  const actualCost = page.getByRole('form', { name: '실제 비용 기록' });
  await actualCost.getByLabel('설명').fill('상품 매입 실결제');
  await actualCost.getByLabel('금액 (원)').fill('80000');
  await actualCost.getByRole('button', { name: '비용 추가' }).click();
  await page.getByRole('button', { name: '실제 원가 마감' }).click();

  const sale = page.getByRole('form', { name: '판매 결과 기록' });
  await sale.getByLabel('재고 로트').selectOption({ index: 1 });
  await sale.getByLabel('채널', { exact: true }).fill('자사몰');
  await sale.getByLabel('판매수량').fill('2');
  await sale.getByLabel('총매출').fill('30000');
  await sale.getByRole('button', { name: '판매 관측 추가' }).click();

  const decision = page.getByRole('form', { name: '재발주 판단' });
  await decision.getByRole('combobox', { name: '판단 상품' }).selectOption({ index: 1 });
  await decision.getByRole('combobox', { name: '판단', exact: true }).selectOption('REORDER');
  await decision.getByLabel('근거').fill('초기 판매 반응과 마진이 기준을 충족함');
  await decision.getByRole('button', { name: '판단 기록' }).click();

  const ledger = page.locator('.cycle-ledger');
  await expect(ledger).toContainText('판매 관측');
  await expect(ledger).toContainText('1건');
  await expect(ledger).toContainText('재발주');
  await expect(ledger).toContainText('초기 판매 반응과 마진이 기준을 충족함');
});
