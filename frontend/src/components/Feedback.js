export function LoadingState({ label = "데이터를 불러오는 중입니다." }) {
  return <div className="feedback"><span className="spinner" />{label}</div>;
}

export function EmptyState({ title, description, action }) {
  return <div className="feedback empty-state"><strong>{title}</strong><p>{description}</p>{action}</div>;
}

export function ErrorMessage({ message }) {
  return message ? <div className="alert error" role="alert">{message}</div> : null;
}
