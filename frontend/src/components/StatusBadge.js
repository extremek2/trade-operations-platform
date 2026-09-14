const labels = {
  OPEN: "진행 중", ON_HOLD: "보류", COMPLETED: "완료", CANCELLED: "취소", ARCHIVED: "보관",
  NORMAL: "정상", ATTENTION: "확인 필요", URGENT: "즉시 조치",
  PREPARATION: "준비", BOOKING: "부킹", DEPARTED: "출발", IN_TRANSIT: "운송 중",
  ARRIVED: "도착", CUSTOMS: "통관", DELIVERY: "배송",
  DISCOVERED: "발굴", SOURCING: "소싱", REVIEWING: "검토", APPROVED: "승인", ACTIVE: "활성",
  REJECTED: "반려", DISCONTINUED: "중단", DRAFT: "작성 중", RECEIVED: "수신", SELECTED: "선택", EXPIRED: "만료",
  INCOMPLETE: "입력 미완료", COST_COMPLETE: "원가 계산 완료", COMPLETE: "계산 완료",
};

export const displayLabel = (value) => labels[value] || value || "—";

export default function StatusBadge({ value }) {
  return <span className={`badge badge-${String(value).toLowerCase().replace("_", "-")}`}>{displayLabel(value)}</span>;
}
