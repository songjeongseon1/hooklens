// 내가 만든 엔드포인트 목록을 브라우저(localStorage)에 보관한다.
// 로그인이 없는 1차에서는 "이 브라우저가 토큰을 들고 있다 = 주인"이다. 새로고침해도 유지된다.
// 주의: localStorage는 같은 사이트의 JS가 읽을 수 있어 XSS에 약하다 → 화면에 사용자 데이터를 HTML로 넣지 않는다
//       (React는 기본으로 글자로만 출력한다. dangerouslySetInnerHTML을 쓰지 않는다).

const KEY = 'hooklens.endpoints'; // localStorage에 저장할 이름

/** 저장된 엔드포인트 목록. [{ slug, url, name, anonymousToken, expiresAt }] */
export function getEndpoints() {
  try {
    return JSON.parse(localStorage.getItem(KEY)) ?? []; // 없으면 null → 빈 배열
  } catch {
    return []; // 저장값이 깨졌거나 localStorage를 못 쓰는 환경(사생활 보호 모드 등)
  }
}

/** 발급받은 엔드포인트를 목록 맨 앞에 추가한다 (토큰은 발급 응답에서 한 번만 받을 수 있어 꼭 저장해야 한다). */
export function saveEndpoint(endpoint) {
  const others = getEndpoints().filter((e) => e.slug !== endpoint.slug); // 같은 slug가 있으면 빼고
  localStorage.setItem(KEY, JSON.stringify([endpoint, ...others]));    // 새 것을 맨 앞에
}

/** 목록에서 지운다 (만료됐거나 더 안 볼 때). */
export function removeEndpoint(slug) {
  localStorage.setItem(KEY, JSON.stringify(getEndpoints().filter((e) => e.slug !== slug)));
}

/** slug의 익명 토큰. 관리 API의 X-Anonymous-Token 헤더에 쓴다. 없으면 undefined → 서버가 401 */
export function getToken(slug) {
  return getEndpoints().find((e) => e.slug === slug)?.anonymousToken;
}
