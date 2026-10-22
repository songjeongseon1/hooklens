import { useEffect, useState } from 'react';
import { getRequestDetail } from '../api/requests';
import { formatDateTime } from '../utils/time';
import MethodBadge from './MethodBadge';

// JSON이면 보기 좋게 들여쓰기, 아니면 원문 그대로
function prettyBody(body) {
  try {
    return JSON.stringify(JSON.parse(body), null, 2);
  } catch {
    return body;
  }
}

// 이름-값 표 (헤더·쿼리 공용)
function KeyValueTable({ rows }) {
  if (rows.length === 0) return <p className="text-sm text-slate-400">없음</p>;
  return (
    <table className="w-full text-sm">
      <tbody>
        {rows.map(([key, value], i) => (
          <tr key={`${key}-${i}`} className="border-b border-slate-100 align-top">
            <td className="w-1/3 py-1 pr-2 font-mono text-slate-500">{key}</td>
            <td className="break-all py-1 font-mono">{value}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// 선택한 요청 하나의 헤더·쿼리·바디 (REQ-LOG-003)
export default function RequestDetail({ slug, requestId }) {
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false; // 빠르게 다른 요청을 누르면 늦게 온 이전 응답이 화면을 덮지 않게
    setDetail(null);
    setError(null);
    getRequestDetail(slug, requestId)
      .then((d) => !ignore && setDetail(d))
      .catch((e) => !ignore && setError(e.message));
    return () => {
      ignore = true;
    };
  }, [slug, requestId]); // 다른 요청을 고르면 다시 불러온다

  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!detail) return <p className="text-sm text-slate-400">불러오는 중...</p>;

  // 헤더 {이름: [값...]} → [이름, 값] 줄들 (같은 이름이 여러 번 왔으면 줄도 여러 개)
  const headerRows = Object.entries(detail.headers).flatMap(([name, values]) => values.map((v) => [name, v]));
  // 쿼리 문자열은 서버가 원본 그대로 준다 → 브라우저의 URLSearchParams로 나눈다(디코딩 포함)
  const queryRows = [...new URLSearchParams(detail.queryString ?? '').entries()];

  return (
    <div className="space-y-5 rounded-xl border border-slate-200 bg-white p-4">
      <div>
        <div className="flex items-center gap-2">
          <MethodBadge method={detail.method} />
          <span className="break-all font-mono">{detail.path}</span>
        </div>
        <p className="mt-1 text-xs text-slate-500">
          {formatDateTime(detail.receivedAt)} · {detail.ip} · {detail.sizeBytes} bytes
        </p>
      </div>
      <section>
        <h3 className="mb-1 text-sm font-semibold">쿼리</h3>
        <KeyValueTable rows={queryRows} />
      </section>
      <section>
        <h3 className="mb-1 text-sm font-semibold">헤더</h3>
        <KeyValueTable rows={headerRows} />
      </section>
      <section>
        <h3 className="mb-1 text-sm font-semibold">
          바디 {detail.contentType && <span className="font-normal text-slate-400">({detail.contentType})</span>}
        </h3>
        {/* <pre>에 글자로 넣는다 → 바디에 HTML·스크립트가 있어도 실행되지 않는다(React가 이스케이프) */}
        <pre className="max-h-96 overflow-auto rounded bg-slate-900 p-3 text-xs text-slate-100">
          {detail.body ? prettyBody(detail.body) : '(비어 있음)'}
        </pre>
      </section>
    </div>
  );
}
