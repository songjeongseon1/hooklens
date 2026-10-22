import { useEffect, useRef, useState } from 'react';
import { getToken } from '../api/tokenStore';

const RECONNECT_DELAY_MS = 3000; // 끊기면 3초 뒤 다시 연결

/**
 * 새 요청을 실시간으로 받는 훅 (REQ-LOG-001).
 *
 * EventSource 대신 fetch를 쓰는 이유: EventSource는 커스텀 헤더를 못 보내서 X-Anonymous-Token을 실을 수 없다.
 * 토큰을 URL(?token=)에 넣으면 Nginx 로그·브라우저 기록에 남는다. fetch는 헤더를 그대로 보낼 수 있다(CLAUDE.md 결정).
 * 대신 EventSource가 해 주던 일(메시지 해석, 재연결)을 직접 한다.
 *
 * @param slug       엔드포인트
 * @param onRequest  새 요청 요약이 올 때마다 호출 (목록 API의 한 줄과 같은 모양)
 * @param onReconnect 끊겼다가 다시 연결됐을 때 호출 → 끊긴 사이 놓친 요청을 목록 API로 다시 불러온다
 * @returns 연결 상태: 'connecting' | 'open' | 'reconnecting' | 'expired' | 'error'
 */
export function useRequestStream(slug, { onRequest, onReconnect }) {
  const [status, setStatus] = useState('connecting');

  // 콜백을 ref에 담는다: 화면이 다시 그려질 때마다 콜백 함수가 새로 만들어지는데,
  // 그걸 useEffect 의존성에 넣으면 그때마다 연결을 끊고 다시 맺게 된다. ref는 바뀌어도 재연결을 일으키지 않는다.
  const handlers = useRef({ onRequest, onReconnect });
  useEffect(() => {
    handlers.current = { onRequest, onReconnect }; // 항상 최신 콜백을 가리키게
  });

  useEffect(() => {
    const controller = new AbortController(); // 화면을 떠나면 abort()로 fetch 연결을 끊는 스위치
    let retryTimer;
    let isFirstConnect = true;

    async function connect() {
      setStatus(isFirstConnect ? 'connecting' : 'reconnecting');
      try {
        const response = await fetch(`/api/endpoints/${slug}/stream`, {
          headers: { 'X-Anonymous-Token': getToken(slug) ?? '' },
          signal: controller.signal, // abort()하면 이 fetch와 읽기가 즉시 중단된다
        });
        if (!response.ok) {
          // 401·403·404·410은 다시 연결해도 결과가 같다 → 재연결하지 않는다
          setStatus(response.status === 410 ? 'expired' : 'error');
          return;
        }
        setStatus('open');
        if (!isFirstConnect) handlers.current.onReconnect?.(); // 끊긴 사이 놓친 요청 보충 (SSE는 알림용, 정확한 목록은 API가 기준)
        isFirstConnect = false;

        await readEvents(response.body, (event, data) => {
          if (event === 'request') handlers.current.onRequest?.(JSON.parse(data)); // connected 등 다른 이벤트는 무시
        });
      } catch {
        if (controller.signal.aborted) return; // 우리가 일부러 끊었다(화면 떠남) → 재연결 안 함
        // 그 외(네트워크 끊김 등)는 아래에서 재연결
      }
      if (!controller.signal.aborted) {
        // 스트림이 끝났다: 서버의 30분 시간 초과, 서버 재시작, 네트워크 끊김 → 잠시 후 다시 연결
        setStatus('reconnecting');
        retryTimer = setTimeout(connect, RECONNECT_DELAY_MS);
      }
    }

    connect();
    return () => {
      // 정리 함수: 화면을 떠나거나 slug가 바뀌면 연결을 끊는다 (안 하면 연결이 계속 쌓인다)
      controller.abort();
      clearTimeout(retryTimer);
    };
  }, [slug]); // slug가 바뀔 때만 새로 연결

  return status;
}

/**
 * SSE 해석기: 바이트 스트림 → 줄 → 메시지 → (event, data).
 * 서버가 보내는 모양:  event:request \n data:{...} \n (빈 줄 = 메시지 끝)  /  ": heartbeat"(주석)
 */
async function readEvents(body, onEvent) {
  const reader = body.pipeThrough(new TextDecoderStream()).getReader(); // 바이트 → 글자(UTF-8)
  let buffer = '';       // 아직 줄이 끝나지 않은 조각
  let event = 'message'; // 이번 메시지의 종류 (event: 줄이 없으면 기본값)
  let data = [];         // 이번 메시지의 data: 줄들

  while (true) {
    const { value, done } = await reader.read(); // 다음 조각이 올 때까지 기다린다 (네트워크는 줄 단위로 안 온다)
    if (done) return;                             // 서버가 응답을 끝냄
    buffer += value;
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop(); // 마지막 조각은 줄이 덜 왔을 수 있어 다음 조각과 합친다

    for (const line of lines) {
      if (line === '') {
        // 빈 줄 = 메시지 하나 끝 → 모은 data를 전달하고 초기화
        if (data.length > 0) onEvent(event, data.join('\n'));
        event = 'message';
        data = [];
      } else if (line.startsWith(':')) {
        // 주석(하트비트) → 무시. 연결 유지용일 뿐이다
      } else if (line.startsWith('event:')) {
        event = line.slice('event:'.length).trim();
      } else if (line.startsWith('data:')) {
        data.push(line.slice('data:'.length).replace(/^ /, '')); // "data: x"처럼 공백이 하나 있으면 뗀다(규격)
      }
    }
  }
}
