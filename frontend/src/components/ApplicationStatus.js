export const applicationLabels = { PENDING_EMAIL: "이메일 확인 필요", PENDING_REVIEW: "관리자 검토 대기", APPROVED: "승인 완료", REJECTED: "반려" };
export const formatDate = value => value ? new Date(value).toLocaleString("ko-KR") : "—";
export default function ApplicationStatus({ value }) {
  return <span className={`badge application-${value?.toLowerCase()}`}>{applicationLabels[value] || "상태 확인 필요"}</span>;
}
export function ApplicationSummary({ application }) {
  return <dl className="detail-list application-summary">
    <div><dt>회사명</dt><dd>{application.organizationName}</dd></div>
    <div><dt>사업자번호</dt><dd>{application.businessNumber || "미입력"}</dd></div>
    <div><dt>신청자</dt><dd>{application.applicantName}</dd></div>
    <div><dt>이메일</dt><dd>{application.applicantEmail}</dd></div>
    <div><dt>연락처</dt><dd>{application.phone || "미입력"}</dd></div>
    <div><dt>신청일</dt><dd>{formatDate(application.submittedAt)}</dd></div>
  </dl>;
}
