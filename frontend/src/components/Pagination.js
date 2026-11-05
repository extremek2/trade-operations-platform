export default function Pagination({ page, totalPages, onChange, disabled = false }) {
  if (totalPages <= 1) return null;
  return <nav className="pagination-controls" aria-label="페이지 이동">
    <button className="button secondary" disabled={disabled || page === 0} onClick={() => onChange(page - 1)}>이전 페이지</button>
    <span>{page + 1} / {totalPages}</span>
    <button className="button secondary" disabled={disabled || page + 1 >= totalPages} onClick={() => onChange(page + 1)}>다음 페이지</button>
  </nav>;
}
