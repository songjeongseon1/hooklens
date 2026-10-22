import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { getEndpoints } from '../api/tokenStore';
import { getRequests } from '../api/requests';
import { useRequestStream } from '../hooks/useRequestStream';
import CopyButton from '../components/CopyButton';
import RequestList from '../components/RequestList';
import RequestDetail from '../components/RequestDetail';
import ResponseSettingsForm from '../components/ResponseSettingsForm';
import { formatDateTime } from '../utils/time';

const STATUS_LABEL = {
  connecting: ['연결 중', 'bg-slate-400'],
  open: ['실시간 연결됨', 'bg-emerald-500'],
  reconnecting: ['다시 연결 중', 'bg-amber-500'],
  expired: ['만료됨', 'bg-red-500'],
  error: ['연결 실패', 'bg-red-500'],
};

// 새 요청들을 기존 목록과 합친다: id로 중복을 없애고 최신순(id 큰 순) 정렬
function merge(current, incoming) {
  const byId = new Map(current.map((r) => [r.requestId, r]));
  incoming.forEach((r) => byId.set(r.requestId, r));
  return [...byId.values()].sort((a, b) => b.requestId - a.requestId);
}

// 엔드포인트 화면: URL 복사, 요청 목록(실시간) + 상세, 응답 설정
export default function EndpointPage() {
  const { slug } = useParams(); // 주소 /e/:slug 의 slug
  const endpoint = getEndpoints().find((e) => e.slug === slug);
  const [tab, setTab] = useState('requests');
  const [requests, setRequests] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selectedId, setSelectedId] = useState(null);
  const [error, setError] = useState(null);

  // 첫 페이지 불러오기 — 처음 들어올 때, 그리고 SSE가 다시 연결됐을 때(놓친 요청 보충)
  const loadFirstPage = useCallback(() => {
    getRequests(slug)
      .then((page) => {
        setRequests((current) => merge(current, page.items));
        setNextCursor((cursor) => cursor ?? page.nextCursor); // 이미 더 보기로 내려간 커서는 유지
      })
      .catch((e) => setError(e.message));
  }, [slug]);

  useEffect(() => {
    if (endpoint) loadFirstPage();
  }, [endpoint?.slug, loadFirstPage]); // eslint-disable-line react-hooks/exhaustive-deps

  // 실시간: 새 요청이 오면 목록 맨 위에 붙인다
  const status = useRequestStream(slug, {
    onRequest: (summary) => setRequests((current) => merge(current, [summary])),
    onReconnect: loadFirstPage,
  });

  async function loadMore() {
    setLoadingMore(true);
    try {
      const page = await getRequests(slug, nextCursor); // 커서 = 지금 목록의 마지막 다음부터
      setRequests((current) => merge(current, page.items));
      setNextCursor(page.nextCursor); // null이면 더 없음 → "더 보기" 사라짐
    } catch (e) {
      setError(e.message);
    } finally {
      setLoadingMore(false);
    }
  }

  if (!endpoint) {
    // 토큰이 이 브라우저에 없다 = 주인이 아니거나 다른 브라우저에서 만든 것
    return (
      <p className="text-slate-600">
        이 브라우저에서 만든 엔드포인트가 아니에요. <Link to="/" className="text-blue-600">홈으로</Link>
      </p>
    );
  }

  const [statusText, statusColor] = STATUS_LABEL[status];
  const tabClass = (name) => `px-3 py-2 text-sm ${tab === name ? 'border-b-2 border-blue-600 font-semibold' : 'text-slate-500'}`;
  return (
    <div className="space-y-4">
      <section className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="flex items-center justify-between">
          <h1 className="font-semibold">{endpoint.name ?? '이름 없음'}</h1>
          <span className="flex items-center gap-1.5 text-sm text-slate-600">
            <span className={`h-2 w-2 rounded-full ${statusColor}`} />
            {statusText}
          </span>
        </div>
        <div className="mt-2 flex items-center gap-2">
          <code className="flex-1 truncate rounded bg-slate-100 px-3 py-1.5 text-sm">{endpoint.url}</code>
          <CopyButton text={endpoint.url} />
        </div>
        <p className="mt-1 text-xs text-slate-500">{formatDateTime(endpoint.expiresAt)}까지 사용할 수 있어요.</p>
      </section>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <nav className="flex border-b border-slate-200">
        <button type="button" className={tabClass('requests')} onClick={() => setTab('requests')}>요청 ({requests.length})</button>
        <button type="button" className={tabClass('response')} onClick={() => setTab('response')}>응답 설정</button>
      </nav>

      {tab === 'requests' ? (
        <div className="grid gap-4 md:grid-cols-2">
          <RequestList
            requests={requests}
            selectedId={selectedId}
            onSelect={setSelectedId}
            hasMore={nextCursor !== null}
            loadingMore={loadingMore}
            onLoadMore={loadMore}
          />
          {selectedId ? <RequestDetail slug={slug} requestId={selectedId} /> : <p className="text-sm text-slate-400">왼쪽에서 요청을 고르면 자세히 보여요.</p>}
        </div>
      ) : (
        <ResponseSettingsForm slug={slug} />
      )}
    </div>
  );
}
