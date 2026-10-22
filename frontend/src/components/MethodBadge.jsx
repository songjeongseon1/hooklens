// HTTP 메서드를 색깔 뱃지로 (GET 초록, POST 파랑, ...). 목록에서 한눈에 구분하려고.
const COLORS = {
  GET: 'bg-emerald-100 text-emerald-700',
  POST: 'bg-blue-100 text-blue-700',
  PUT: 'bg-amber-100 text-amber-700',
  PATCH: 'bg-violet-100 text-violet-700',
  DELETE: 'bg-red-100 text-red-700',
};

export default function MethodBadge({ method }) {
  const color = COLORS[method] ?? 'bg-slate-100 text-slate-700'; // 목록에 없는 메서드(HEAD 등)는 회색
  return (
    <span className={`inline-block w-16 rounded px-1.5 py-0.5 text-center font-mono text-xs font-semibold ${color}`}>
      {method}
    </span>
  );
}
