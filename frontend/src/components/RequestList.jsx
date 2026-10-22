import MethodBadge from './MethodBadge';
import { timeAgo } from '../utils/time';

// 요청 목록 (최신순). 누르면 상세를 연다. 맨 아래 "더 보기"는 커서로 다음 페이지를 불러온다.
export default function RequestList({ requests, selectedId, onSelect, hasMore, loadingMore, onLoadMore }) {
  if (requests.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
        아직 들어온 요청이 없어요. 위 URL로 웹훅을 보내면 여기에 바로 나타나요.
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white">
      <ul className="divide-y divide-slate-100">
        {requests.map((r) => (
          <li key={r.requestId}>
            <button
              type="button"
              onClick={() => onSelect(r.requestId)}
              className={`flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-slate-50 ${
                r.requestId === selectedId ? 'bg-blue-50' : '' // 선택된 요청은 파란 배경
              }`}
            >
              <MethodBadge method={r.method} />
              <span className="flex-1 truncate font-mono text-sm">
                {r.path}
                {r.queryString && <span className="text-slate-400">?{r.queryString}</span>}
              </span>
              <span className="shrink-0 text-xs text-slate-500">{timeAgo(r.receivedAt)}</span>
            </button>
          </li>
        ))}
      </ul>
      {hasMore && (
        <button
          type="button"
          onClick={onLoadMore}
          disabled={loadingMore}
          className="w-full border-t border-slate-100 py-2 text-sm text-blue-600 hover:bg-slate-50 disabled:opacity-50"
        >
          {loadingMore ? '불러오는 중...' : '더 보기'}
        </button>
      )}
    </div>
  );
}
