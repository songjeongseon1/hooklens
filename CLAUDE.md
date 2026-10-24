# HookLens — 웹훅 디버거

외부 서비스(결제사, 깃허브 등)가 보내는 웹훅을 고유 URL로 받아 실시간으로 보여주고, 원하는 응답을 돌려주는 개발자 도구.
개인 포트폴리오 프로젝트이며, 1차 목표는 2주 안에 P0(MVP) 기능을 완성하고 AWS EC2에 배포하는 것이다. P1은 2차 개발로 진행한다.

## 진행 방식 (중요)

- 나는 이 프로젝트로 공부하면서 면접에서 설명할 수 있어야 한다. 코드를 작성하기 전에 **무엇을, 왜 이렇게 하는지** 짧게 설명해 줘.
- 한 번에 한 기능(요구사항 ID 하나)씩 작게 진행한다. 큰 변경은 먼저 계획을 보여주고 내 확인을 받는다.
- 기술 선택에 대안이 있으면(예: SSE vs WebSocket) 비교를 한두 줄로 알려 줘.
- 기능이 끝나면 테스트를 실행해 통과를 확인하고, 커밋 메시지를 제안해 줘.
- 설명과 커밋 메시지는 한국어로 작성한다.

## 현재 진행 상황

- 1주차 진행 중. 프로젝트 세팅 완료(공통 응답 `ApiResponse`, `ErrorCode`, `GlobalExceptionHandler`, local/prod 프로필).
- 완료: REQ-EP-001 (비회원 엔드포인트 발급 `POST /api/endpoints`)
- 완료: REQ-EP-002 도메인·서비스 (`Endpoint.isExpired`, `EndpointService.getActiveBySlug` → 404/410)
- 완료: REQ-HOOK-001 (웹훅 수신·저장 `/h/{slug}/**` → `request_log`)
  - `HookRequestReader`가 바디를 원본 바이트로 직접 읽는다. `spring.mvc.formcontent.filter.enabled=false` (켜면 PUT 폼 바디가 비어 저장됨).
  - `/h/**`의 404·410은 관리 API와 같은 `ApiResponse` JSON 형식으로 응답한다(결정됨).
  - 지금은 항상 `200 {"ok":true}` 응답 (`HookReceiveController.DEFAULT_RESPONSE_BODY`).
  - 한계: 바이너리 바디는 UTF-8 문자열로 저장해 깨질 수 있다. IP는 `getRemoteAddr` — 배포 시 Nginx 뒤 실제 IP 처리 필요.
- 완료: REQ-HOOK-002 (설정된 응답 반환, 없으면 200 `{"ok":true}`)
  - `ResponseRule`(기본 규칙만, priority·match_*는 P2) → `HookReceiveService.receive`가 로그 저장 후 `Optional<ResponseRule>` 반환 → 컨트롤러가 `HookResponseBuilder`로 응답 생성
  - 응답 헤더 JSON 형식은 `{"이름": "값"}` (요청 로그의 `{"이름": ["값"]}`과 다름 — 사용자가 직접 입력하는 값이라 단순하게). 전송 제어 헤더(Content-Length 등)는 무시.
  - 지연(`delayMs`)은 트랜잭션이 끝난 뒤 컨트롤러에서 `Thread.sleep` → 로그는 바로 보이고 DB 커넥션을 붙잡지 않는다.
  - 가상 스레드 사용(`spring.threads.virtual.enabled: true`). 실행 시 요청 스레드 이름이 `tomcat-handler-N`이면 켜진 것(MockMvc 테스트로는 확인 불가).
- 완료: REQ-LOG-002 (요청 목록 최신순 커서 조회 `GET /api/endpoints/{slug}/requests?cursor=&size=`)
  - 관리 API 인증: `EndpointService.getOwnedBySlug(slug, token)` — 토큰 없음 401, 틀림 403, 이후 관리 API는 모두 이걸 쓴다. 비교는 `MessageDigest.isEqual`(타이밍 공격 방지).
  - 커서: `size + 1`개 조회해 다음 페이지 여부 판단(COUNT 없음), 첫 페이지는 cursor = `Long.MAX_VALUE`. size 1~100(기본 20).
  - `HandlerMethodValidationException`(`@RequestParam` 검증)·`MethodArgumentTypeMismatchException`(타입 불일치)도 400 `INVALID_INPUT`으로 처리.
  - 목록은 요약만(`RequestLogSummary`), 헤더·바디는 상세(REQ-LOG-003)에서.
- 완료: REQ-LOG-003 (요청 상세 `GET /api/endpoints/{slug}/requests/{requestId}`)
  - 헤더는 Map으로 바꿔 JSON 객체로, 쿼리는 원본 문자열 그대로(화면에서 URLSearchParams).
  - IDOR 방어: `findByIdAndEndpointId`로 "id + 내 엔드포인트" 조건을 쿼리에 넣는다. 남의 요청 id면 403이 아닌 404 `REQUEST_NOT_FOUND`(존재 여부 숨김).
- 1주차 백엔드 범위(EP-001·002, HOOK-001·002, LOG-002·003) 완료.
- 완료: REQ-LOG-001 백엔드 (SSE `GET /api/endpoints/{slug}/stream`, 헤더 인증은 getOwnedBySlug)
  - `RequestStreamRegistry`: 엔드포인트 id → 연결 Set (ConcurrentHashMap, 서버 1대 메모리). 연결 직후 `event: connected`, 30초마다 `: heartbeat` 주석(@Scheduled, `SchedulingConfig`), 실패·종료·시간 초과(30분) 시 목록에서 제거.
  - 수신 → `HookReceiveService`가 `RequestReceivedEvent`(endpointId + `RequestLogSummary`) 발행 → `RequestReceivedListener`가 `@TransactionalEventListener(AFTER_COMMIT)`로 받아 `event: request` 전송. 커밋 전 알림(상세 404)·롤백 시 유령 알림 방지.
  - 테스트: `RequestStreamApiTest`(MockMvc, 구독·401·403), `RequestStreamE2ETest`(RANDOM_PORT + HttpClient로 실제 스트림 수신).
  - 화면 쪽 fetch 스트림 훅은 React 단계에서 만든다.
- 완료: REQ-RES-001 (기본 응답 설정 `GET`·`PUT /api/endpoints/{slug}/response`)
  - PUT = upsert(있으면 `ResponseRule.update`로 변경 감지 UPDATE, 없으면 INSERT), 멱등. 설정 전 GET은 기본 응답 + `custom: false`.
  - 검증: statusCode 100~599, delayMs 0~5000, body 20,000자, 헤더 20개. 헤더 주입 방지 — 이름은 허용 문자 정규식(화이트리스트), 값은 `\r`·`\n` 금지 → 400 + 어느 헤더인지 메시지(`BusinessException(ErrorCode, message)` 추가).
  - 한계: 같은 엔드포인트에 처음 저장하는 요청이 동시에 오면 기본 규칙이 두 개 생길 수 있음(P2에서 잠금·제약).
- **백엔드 P0 전부 완료.** 다음: React 화면 → EC2 배포 (목표: 2026-10-23까지 배포).
- 진행 방식 변경(10/22): 프론트·배포는 Claude가 작성하고 코드 주석·줄별 설명으로 이해하는 방식. 배포 DB는 RDS 대신 **EC2 안 MySQL 컨테이너**(나중에 접속 주소만 바꿔 RDS 이전 가능).
- 완료: 프론트 MVP (`frontend/`, 브라우저로 발급→실시간 수신→상세→응답 설정→웹훅 반영까지 확인)
  - 화면: `/` HomePage(발급, 내 엔드포인트 목록), `/e/:slug` EndpointPage(URL 복사, 요청 목록+상세, 응답 설정 탭)
  - `src/api/client.js`: axios 인스턴스, 응답 인터셉터로 `{success,data,error}`에서 data 추출·error.message로 Error. `tokenStore.js`: localStorage에 엔드포인트·토큰 보관.
  - `src/hooks/useRequestStream.js`: fetch 스트림으로 SSE 수신(헤더 인증), 직접 파싱, 3초 후 재연결, 재연결 시 목록 API로 보충. 목록은 requestId로 중복 제거·정렬(`merge`).
  - React Router는 API를 확실히 아는 7.x(`~7.18.4`)로 고정(최신 8.x 아님).
  - 이 PC는 8080을 Jenkins가 쓴다 → 백엔드를 다른 포트로 띄우고 `BACKEND_URL=http://localhost:포트 npm run dev`(Vite 프록시 대상).
- 완료: 배포 준비 (로컬에서 운영 구성 그대로 띄워 화면·발급·SSE·실제 IP·시간대 확인). 절차는 `docs/deploy.md`.
  - `docker-compose.prod.yml`(프로젝트 이름 `hooklens-prod`): mysql(포트 비공개, 볼륨, `--performance-schema=OFF`) + backend(`backend/Dockerfile`, 포트 비공개, mem 512m) + nginx(`frontend/Dockerfile`, 80만 공개).
  - 테이블: `deploy/mysql/init/01-schema.sql`(엔티티에서 Hibernate로 생성) — MySQL이 처음 뜰 때만 실행. **엔티티를 바꾸면 이 파일도 다시 생성**(2차엔 Flyway).
  - `frontend/nginx.conf`: 정적 파일 + `/api`·`/h` 프록시 + stream만 `proxy_buffering off`·`proxy_read_timeout 1h` + `try_files ... /index.html`(SPA).
  - 실제 IP: Nginx가 `X-Forwarded-For` 전달 + prod `server.forward-headers-strategy: native`. Redis는 운영에 안 띄움(`management.health.redis.enabled: false`).
  - 운영 비밀 값은 `.env.prod`(Git 제외), 예시는 `.env.prod.example`. EC2는 t3.micro + 스왑 2GB.
- EC2 실제 배포는 보류(10/24 결정). 대신 포트폴리오 자료 작성: `README.md`(GitHub), `docs/portfolio/HookLens_프로젝트기술서.pdf`(A4 7쪽), `docs/portfolio/HookLens_포트폴리오.pdf`(가로 9장). 원본은 같은 폴더의 `technical-spec.html`, `portfolio.html` → Chrome headless로 PDF 출력. 화면 캡처는 `docs/images/`.
  - `docs/planning.md`(기획서), `docs/api.md`(API 명세서 — 응답 예시는 로컬 운영 구성에서 실제 호출한 값) 추가. PDF는 `docs/portfolio/md2html.py`(+`doc.css`)로 HTML을 만든 뒤 Chrome headless로 출력: `python docs/portfolio/md2html.py docs/api.md docs/portfolio/api-spec.html "바닥글" "" docs/portfolio/doc.css` (기획서는 이미지 경로 접두어 `../`). **API를 바꾸면 api.md도 같이 고친다.**
  - 문체 원칙: 이모지·과장 표현 없이 "문제 → 비교 → 선택 → 결과", 실제 수치(테스트 59개, 커밋 26개, PR 8개, 10일).
- 다음(선택): EC2 생성·배포(`docs/deploy.md`) → 깃허브 웹훅으로 실제 확인.
- 남은 일: 회원 엔드포인트(만료 없음) 생성 메서드가 생기면(REQ-AUTH) `isExpired` 테스트 추가
- 기능을 마칠 때마다 이 섹션을 갱신한다.

## 명령어

- 인프라 실행(루트): `docker compose up -d` / 종료: `docker compose down`
- 백엔드 테스트: `cd backend && ./gradlew test` (Windows PowerShell은 `.\gradlew test`) — Testcontainers 테스트는 Docker가 켜져 있어야 한다.
- 백엔드 실행: `cd backend && ./gradlew bootRun` (기본 프로필 `local`, 루트 `.env`를 자동으로 읽음)
- 헬스 체크: http://localhost:8080/actuator/health
- 프론트(2주차~): `cd frontend && npm run dev`
- 운영 구성 실행(로컬·EC2 공통): `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build` → http://localhost (상태: `docker compose -p hooklens-prod ps`, 로그: `docker compose -p hooklens-prod logs -f backend`)

## 기술 스택

- 백엔드: Java 21, Spring Boot 4, Spring Data JPA, Gradle(Groovy), Lombok, Validation, Actuator
- 실시간: SSE (SseEmitter) — WebSocket은 이미 다른 프로젝트에서 써봤고, 단방향 푸시라 SSE 선택
- DB: MySQL 8 (로컬은 Docker Compose, 포트 3307 / 운영 1차는 EC2 안 MySQL 컨테이너, RDS는 이후)
- 캐시·제한: Redis 7 (Rate Limit, Refresh 토큰, 다중 서버용 Pub/Sub)
- 인증: Spring Security + JWT (2차 개발에서 추가, 그 전에는 Security 의존성 넣지 않음), 비회원은 익명 토큰
- 프론트엔드: React 19, JavaScript(ES6+), Vite 8, React Router, Axios, Tailwind CSS 4
- 테스트: JUnit 5, Testcontainers(MySQL·Redis 실제 컨테이너로 통합 테스트)
- 배포: Docker, AWS EC2 + RDS, Nginx, GitHub Actions
- 문서: Swagger(springdoc-openapi)

### 버전 주의 (예전 버전 방식으로 코드를 쓰지 말 것)

- **Spring Boot 4.0.x**: 웹 스타터는 `spring-boot-starter-webmvc`, JSON은 Jackson 3(`tools.jackson.*` 패키지, 단 어노테이션은 그대로 `com.fasterxml.jackson.annotation`), `jakarta.*` 사용. Boot 2·3 예제를 그대로 옮기지 말고, 확실하지 않은 API는 먼저 확인한다.
- **Tailwind CSS 4**: `tailwind.config.js`·`@tailwind` 지시어를 쓰지 않는다. CSS에서 `@import "tailwindcss";`, 설정은 `@theme`, Vite 플러그인은 `@tailwindcss/vite`.
- **React 19 + Vite 8**: 함수 컴포넌트와 훅만 사용한다.
- **Testcontainers 2.x**: 아티팩트는 `testcontainers-mysql` 형식, 클래스는 `org.testcontainers.mysql.MySQLContainer`(제네릭 없음). 통합 테스트는 `@Import(TestcontainersConfiguration.class)` + `@ActiveProfiles("test")`.
- MockMvc 어노테이션은 `org.springframework.boot.webmvc.test.autoconfigure` 패키지(`spring-boot-starter-webmvc-test`).
- springdoc은 아직 `build.gradle`에 없다. 처음 필요한 기능에서 추가를 제안하고 확인을 받는다.

## 폴더 구조

```
hooklens/
├── backend/            Spring Boot (패키지: com.hooklens)
├── frontend/           React (2주차에 생성)
├── docker-compose.yml       로컬 개발용 MySQL, Redis
├── docker-compose.prod.yml  운영용 mysql + backend + nginx
├── deploy/mysql/init/       운영 DB 초기 테이블 SQL
├── docs/                    study-notes.md(공부 노트), deploy.md(배포 절차)
├── .env                비밀 값 — 절대 커밋하지 않음
└── CLAUDE.md
```

백엔드 패키지는 기능(도메인)별로 나눈다: `endpoint`, `hook`(수신), `log`(요청 로그), `response`(응답 규칙), `replay`, `auth`, `global`(공통 응답, 예외, 설정).

## 규칙

- **비밀 값**: DB 비밀번호, JWT 키 등은 환경 변수로만 주입한다. 코드나 application.yml에 실제 값을 쓰지 않는다.
- **프로필**: `local`, `prod`로 분리한다.
- **공통 응답**: 관리 API(`/api/**`)는 `{ "success": boolean, "data": ..., "error": { "code", "message" } }` 형식을 쓴다. 웹훅 수신(`/h/**`)은 사용자가 설정한 응답을 그대로 반환하므로 이 형식을 쓰지 않는다.
- **에러 코드**: `ENDPOINT_NOT_FOUND`(404), `REQUEST_NOT_FOUND`(404), `ENDPOINT_EXPIRED`(410), `FORBIDDEN_ENDPOINT`(403), `PAYLOAD_TOO_LARGE`(413), `RATE_LIMIT_EXCEEDED`(429), `REPLAY_TARGET_BLOCKED`(400), `REPLAY_FAILED`(502), `INVALID_INPUT`(400), `UNAUTHORIZED`(401), `INTERNAL_ERROR`(500). 원본은 `global/exception/ErrorCode.java` — 추가·변경 시 이 목록도 함께 고친다.
- **페이지네이션**: 요청 로그는 커서 방식(`?cursor={마지막 id}&size=20`, 응답에 `nextCursor`).
- **시간**: 모든 계층을 KST(Asia/Seoul)로 통일한다. JPA `hibernate.jdbc.time_zone`, JDBC `serverTimezone`은 설정돼 있고, 배포 시 컨테이너에도 `TZ=Asia/Seoul`을 준다(EC2·RDS 기본값은 UTC라 빠뜨리면 9시간 어긋남). API 응답은 ISO-8601에 오프셋 포함(`2026-10-13T16:03:55+09:00`).
- **비회원 인증**: 엔드포인트 생성 시 익명 토큰(랜덤 32바이트)을 한 번만 응답으로 내려주고, DB에는 SHA-256 해시만 저장한다. 이후 관리 API 요청은 `X-Anonymous-Token` 헤더로 보낸다.
- **보안**: 리플레이 대상 URL이 사설·루프백·링크로컬 IP로 해석되면 차단한다(SSRF 방지). DNS 해석 결과 IP로 검사한다.

## 프론트엔드 규칙 (2주차~)

### 진행 방식
- 화면 하나, 컴포넌트 하나 단위로 작게 만든다. 한 번에 여러 화면을 만들지 않는다.
- 새 개념(훅, 라이브러리, 브라우저 API)을 처음 쓸 때는 코드 전에 무엇을, 왜 쓰는지 짧게 설명한다. 이미 쓴 패턴의 반복(스타일, 레이아웃)은 설명 없이 바로 만들어도 된다.
- 만든 뒤에는 브라우저에서 확인할 동작(어떤 버튼을 누르면 무엇이 보여야 하는지)을 알려 준다.
- 에러를 고칠 때는 원인을 먼저 한두 줄로 설명하고 고친다.

### 코드 규칙
- 폴더: `src/pages`(라우트 화면), `src/components`(재사용 UI), `src/api`(서버 호출), `src/hooks`(커스텀 훅)
- 컴포넌트는 한 파일에 하나, 150줄을 넘으면 나눈다. 파일 이름은 PascalCase(`RequestList.jsx`).
- 서버 호출은 `src/api/`에만 둔다. 컴포넌트에서 axios를 직접 호출하지 않는다.
- 응답은 공통 형식 `{ success, data, error }`이므로 `src/api/`에서 `data`를 꺼내고, 실패 시 `error.message`를 담은 에러를 던진다.
- 스타일은 Tailwind 클래스만 쓴다. 별도 CSS 파일·CSS-in-JS 라이브러리를 추가하지 않는다.
- 상태 관리 라이브러리(Redux, Zustand 등)는 쓰지 않는다. `useState`·`useContext`로 충분한 규모다.
- 시간은 서버가 주는 ISO-8601(+09:00)을 그대로 파싱해 표시한다(목록은 "3분 전", 상세는 전체 시각).

### 개발 환경
- 로컬에서는 Vite 프록시로 `/api`, `/h`를 `http://localhost:8080`에 넘긴다 → 백엔드에 CORS 설정을 추가하지 않는다.
- 익명 토큰은 `localStorage`에 slug별로 저장하고, `src/api/`에서 `X-Anonymous-Token` 헤더로 붙인다.

### 미리 알아둘 것
- **SSE 인증(REQ-LOG-001, 결정됨)**: 브라우저 `EventSource`는 커스텀 헤더를 보낼 수 없으므로 **`fetch` 스트림으로 연결해 `X-Anonymous-Token` 헤더를 그대로 쓴다.**
  - 이유: 관리 API와 인증 방식이 하나로 통일되고, 백엔드에 추가 작업이 없으며, 토큰이 URL(Nginx 로그·브라우저 기록)에 남지 않는다.
  - 검토한 대안: 쿼리 파라미터(토큰 URL 노출), 일회용 티켓(티켓 저장소·재연결 처리 필요), 쿠키(인증 방식이 둘로 갈림·CSRF).
  - 구현: 라이브러리 없이 `fetch` → `response.body.getReader()`로 `data:` 줄을 직접 파싱한다(`src/hooks/`의 커스텀 훅, 공부 목적).
  - 재연결: 끊기면 잠시 후 다시 연결하고, 놓친 요청은 목록 API(REQ-LOG-002)로 다시 불러온다. SSE는 새 요청 알림용, 정확한 목록은 API가 기준.
  - 배포 시 Nginx에 `proxy_buffering off;` 필수(없으면 응답을 모았다가 보내 실시간이 안 됨).

## 하지 말 것

- 새 의존성은 추가하기 전에 이유를 설명하고 확인을 받는다.
- `.env` 파일은 읽거나 수정하지 않는다. 변수 이름이 바뀌면 `.env.example`만 고친다.
- 2차 개발(REQ-AUTH-001) 전에는 Spring Security 의존성을 넣지 않는다 — 기본 로그인이 걸려 웹훅 수신 테스트가 막힌다.
- 요청하지 않은 기능(다음 요구사항 ID)까지 미리 구현하지 않는다.
- 운영 프로필의 `ddl-auto`를 `validate` 외 값으로 바꾸지 않는다.

## 브랜치·커밋

- 브랜치: `feature/{요구사항ID}-{설명}` (예: `feature/REQ-HOOK-001-receive`), `fix/{설명}`
- main 직접 푸시 금지, 기능마다 PR 생성
- 커밋: `feat|fix|refactor|test|docs|chore: 설명 (요구사항ID)`
  - 예: `feat: 웹훅 수신 저장 구현 (REQ-HOOK-001)`

## 데이터 모델

- **member**: member_id(PK), email(UK), password(BCrypt), nickname, role, created_at, updated_at
- **endpoint**: endpoint_id(PK), member_id(FK, NULL=비회원), slug(UK, 영숫자 10자), name, anonymous_token_hash(SHA-256), forward_url, expires_at(비회원 24시간), created_at
- **request_log**: request_id(PK), endpoint_id(FK), method, path(VARCHAR 2048), query_string(TEXT), headers(JSON), body(MEDIUMTEXT, 최대 1MB), content_type, ip, size_bytes, received_at — 인덱스 (endpoint_id, request_id DESC), (received_at)
- **response_rule**: rule_id(PK), endpoint_id(FK), priority, is_default, match_method, match_header, match_body_contains, status_code, response_headers(JSON), response_body, delay_ms(0~5000)
- 엔드포인트 삭제 시 요청 로그·응답 규칙 함께 삭제
- Redis: `refresh:{memberId}`(14일), `rate:{endpointId}:{yyyyMMddHHmm}`(2분), 채널 `hook-events`
  - Refresh 토큰은 회원당 1개만 저장 → 다른 기기에서 로그인하면 이전 기기는 재발급 시 로그아웃된다(의도한 단순화).

## 보관 정책 (REQ-LOG-005 스케줄러, 매일 03:00 KST)

- 회원 엔드포인트의 요청 로그: 30일 지난 것 삭제
- 비회원 엔드포인트: `expires_at`이 지난 엔드포인트를 로그·응답 규칙과 함께 삭제 (그 전까지는 수신 시 410)
- 대량 삭제는 한 번에 지우지 않고 1,000건씩 나눠 삭제한다(테이블 잠금 방지).

## 요구사항 (우선순위: P0 = 1차(2주, MVP), P1 = 2차 개발, P2 = 이후)

| ID | 내용 | 우선순위 |
| --- | --- | --- |
| REQ-EP-001 | 비회원도 고유 slug URL 발급 | P0 |
| REQ-EP-002 | 비회원 엔드포인트 24시간 후 만료, 이후 수신 시 410 | P0 |
| REQ-HOOK-001 | `/h/{slug}/**` 모든 메서드 수신, 메서드·경로·쿼리·헤더·바디·IP·시각 저장 | P0 |
| REQ-HOOK-002 | 설정된 응답 반환, 없으면 200 `{"ok":true}` | P0 |
| REQ-LOG-001 | SSE로 새 요청 실시간 반영 | P0 |
| REQ-LOG-002 | 요청 목록 최신순 커서 조회 | P0 |
| REQ-LOG-003 | 요청 상세(헤더·쿼리·바디) | P0 |
| REQ-RES-001 | 기본 응답 설정(상태 코드, 헤더, 바디, 지연) | P0 |
| REQ-EP-003~005 | 내 엔드포인트 목록·수정·삭제, 비회원 엔드포인트 이전 | P1 |
| REQ-HOOK-003 | 엔드포인트당 분당 60건 초과 시 429 | P1 |
| REQ-HOOK-004 | 바디 1MB 초과 시 413 | P1 |
| REQ-LOG-004~005 | 로그 비우기, 매일 새벽 3시 보관 기간 지난 로그 삭제 | P1 |
| REQ-RPL-001~002 | 요청 리플레이, 내부망 차단 | P1 |
| REQ-AUTH-001~002 | 회원가입·로그인(JWT), 토큰 재발급·로그아웃 | P1 |
| REQ-RES-002, RPL-003, UX-001~002 | 조건부 응답, 자동 포워딩, cURL 내보내기, 검색 | P2 |

## 일정 (1차: 2주)

- 1주차: 세팅, 엔드포인트 발급(REQ-EP-001·002), 웹훅 수신·저장(REQ-HOOK-001·002), 요청 목록·상세 조회 API(REQ-LOG-002·003)
- 2주차: SSE 실시간 반영(REQ-LOG-001), React 대시보드·상세 화면, 기본 응답 설정(REQ-RES-001), EC2 + Docker 수동 배포 → **MVP 완성·배포**
- 2차 개발(이후): 회원·JWT, 엔드포인트 관리, Rate Limit·크기 제한, 리플레이·SSRF 차단, GitHub Actions 자동 배포, 로그 정리 스케줄러
- 1차 기간에는 Redis를 쓰는 기능(Rate Limit, Pub/Sub)과 Spring Security를 만들지 않는다. SSE는 서버 1대 기준 메모리 방식으로 시작한다. (Redis 의존성·컨테이너 설정은 그대로 둔다.)
