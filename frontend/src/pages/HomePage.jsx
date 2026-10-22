import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { createEndpoint } from '../api/endpoints';
import { getEndpoints } from '../api/tokenStore';
import { formatDateTime } from '../utils/time';

// 홈: 엔드포인트 만들기 + 이 브라우저에 저장된 내 엔드포인트 목록.
export default function HomePage() {
  const navigate = useNavigate();                 // 코드로 다른 주소로 이동할 때 쓰는 함수
  const [name, setName] = useState('');           // 입력칸 값 (선택)
  const [creating, setCreating] = useState(false); // 요청 중이면 버튼을 막는다 (두 번 눌러 두 개 생기는 것 방지)
  const [error, setError] = useState(null);
  const endpoints = getEndpoints();               // localStorage에서 읽기 (렌더링할 때마다 최신)

  async function handleCreate(event) {
    event.preventDefault(); // form 제출의 기본 동작(페이지 새로고침)을 막는다
    setCreating(true);
    setError(null);
    try {
      const endpoint = await createEndpoint(name.trim()); // 발급 + 토큰을 브라우저에 저장
      navigate(`/e/${endpoint.slug}`);                     // 만들자마자 그 엔드포인트 화면으로
    } catch (e) {
      setError(e.message); // client.js가 서버의 error.message를 담아 준다
      setCreating(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <section className="rounded-xl border border-slate-200 bg-white p-6">
        <h1 className="text-2xl font-bold">웹훅을 받아서 바로 확인하세요</h1>
        <p className="mt-2 text-slate-600">
          고유 URL을 만들고 결제사·깃허브 등의 웹훅 주소로 넣으면, 들어오는 요청이 실시간으로 보입니다.
          비회원 URL은 24시간 동안 쓸 수 있어요.
        </p>
        <form onSubmit={handleCreate} className="mt-4 flex gap-2">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)} // 입력할 때마다 상태를 바꾼다 (제어 컴포넌트)
            maxLength={50}                            // 서버 검증(@Size(max = 50))과 같은 값
            placeholder="이름 (선택) 예: 결제 테스트"
            className="flex-1 rounded-md border border-slate-300 px-3 py-2"
          />
          <button
            type="submit"
            disabled={creating}
            className="rounded-md bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-500 disabled:opacity-50"
          >
            {creating ? '만드는 중...' : '엔드포인트 만들기'}
          </button>
        </form>
        {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      </section>

      <section>
        <h2 className="mb-2 font-semibold">내 엔드포인트</h2>
        {endpoints.length === 0 ? (
          <p className="text-sm text-slate-500">아직 없어요. 위에서 만들어 보세요.</p>
        ) : (
          <ul className="divide-y divide-slate-200 rounded-xl border border-slate-200 bg-white">
            {endpoints.map((e) => {
              const expired = new Date(e.expiresAt) <= new Date(); // 만료 시각이 지났는지
              return (
                <li key={e.slug}>
                  <Link to={`/e/${e.slug}`} className="flex items-center justify-between px-4 py-3 hover:bg-slate-50">
                    <span>
                      <span className="font-medium">{e.name ?? '이름 없음'}</span>
                      <span className="ml-2 font-mono text-sm text-slate-500">{e.slug}</span>
                    </span>
                    <span className={`text-sm ${expired ? 'text-red-500' : 'text-slate-500'}`}>
                      {expired ? '만료됨' : `${formatDateTime(e.expiresAt)}까지`}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
