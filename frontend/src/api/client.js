import axios from 'axios';
import { getToken } from './tokenStore';

// 모든 서버 호출이 쓰는 axios 인스턴스.
// 주소가 '/api'로 시작 → 개발 중엔 Vite 프록시, 운영에선 Nginx가 백엔드로 넘긴다 (프론트는 백엔드 주소를 몰라도 된다).
const client = axios.create({ baseURL: '/api' });

// 응답 가로채기(interceptor): 서버의 공통 형식 { success, data, error }를 여기서 한 번에 처리한다.
// → 컴포넌트는 data만 받고, 실패하면 서버가 준 error.message를 담은 Error를 받는다.
client.interceptors.response.use(
  (response) => response.data.data, // 성공(2xx): data만 꺼내서 넘긴다
  (error) => {
    const body = error.response?.data; // 서버가 응답했다면 { success: false, error: { code, message } }
    const message = body?.error?.message ?? '서버에 연결하지 못했습니다.'; // 응답 자체가 없으면(서버 꺼짐 등) 기본 문구
    const appError = new Error(message);
    appError.code = body?.error?.code;      // 예: ENDPOINT_EXPIRED → 화면이 상황별로 다르게 처리할 수 있다
    appError.status = error.response?.status; // 예: 410
    return Promise.reject(appError);
  },
);

/** 관리 API에 붙일 인증 헤더. 이 브라우저가 저장해 둔 그 엔드포인트의 토큰을 쓴다. */
export function authHeaders(slug) {
  return { 'X-Anonymous-Token': getToken(slug) };
}

export default client;
