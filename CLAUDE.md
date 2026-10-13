# HookLens — 웹훅 디버거

외부 서비스(결제사, 깃허브 등)가 보내는 웹훅을 고유 URL로 받아 실시간으로 보여주고, 원하는 응답을 돌려주는 개발자 도구.
개인 포트폴리오 프로젝트이며, 4주 안에 P0·P1 기능을 완성하고 AWS에 배포하는 것이 목표다.

## 진행 방식 (중요)

- 나는 이 프로젝트로 공부하면서 면접에서 설명할 수 있어야 한다. 코드를 작성하기 전에 **무엇을, 왜 이렇게 하는지** 짧게 설명해 줘.
- 한 번에 한 기능(요구사항 ID 하나)씩 작게 진행한다. 큰 변경은 먼저 계획을 보여주고 내 확인을 받는다.
- 기술 선택에 대안이 있으면(예: SSE vs WebSocket) 비교를 한두 줄로 알려 줘.
- 기능이 끝나면 테스트를 실행해 통과를 확인하고, 커밋 메시지를 제안해 줘.
- 설명과 커밋 메시지는 한국어로 작성한다.

## 현재 진행 상황

- 1주차 진행 중. 프로젝트 세팅 완료(공통 응답 `ApiResponse`, `ErrorCode`, `GlobalExceptionHandler`, local/prod 프로필).
- 다음 작업: REQ-EP-001 (비회원 엔드포인트 발급)
- 기능을 마칠 때마다 이 섹션을 갱신한다.

## 명령어

- 인프라 실행(루트): `docker compose up -d` / 종료: `docker compose down`
- 백엔드 테스트: `cd backend && ./gradlew test` (Windows PowerShell은 `.\gradlew test`) — Testcontainers 테스트는 Docker가 켜져 있어야 한다.
- 백엔드 실행: `cd backend && ./gradlew bootRun` (기본 프로필 `local`, 루트 `.env`를 자동으로 읽음)
- 헬스 체크: http://localhost:8080/actuator/health
- 프론트(2주차~): `cd frontend && npm run dev`

## 기술 스택

- 백엔드: Java 21, Spring Boot 4, Spring Data JPA, Gradle(Groovy), Lombok, Validation, Actuator
- 실시간: SSE (SseEmitter) — WebSocket은 이미 다른 프로젝트에서 써봤고, 단방향 푸시라 SSE 선택
- DB: MySQL 8 (로컬은 Docker Compose, 포트 3307 / 운영은 AWS RDS)
- 캐시·제한: Redis 7 (Rate Limit, Refresh 토큰, 다중 서버용 Pub/Sub)
- 인증: Spring Security + JWT (3주차에 추가, 그 전에는 Security 의존성 넣지 않음), 비회원은 익명 토큰
- 프론트엔드: React 19, JavaScript(ES6+), Vite 8, React Router, Axios, Tailwind CSS 4
- 테스트: JUnit 5, Testcontainers(MySQL·Redis 실제 컨테이너로 통합 테스트)
- 배포: Docker, AWS EC2 + RDS, Nginx, GitHub Actions
- 문서: Swagger(springdoc-openapi)

### 버전 주의 (예전 버전 방식으로 코드를 쓰지 말 것)

- **Spring Boot 4.0.x**: 웹 스타터는 `spring-boot-starter-webmvc`, JSON은 Jackson 3(`tools.jackson.*` 패키지, 단 어노테이션은 그대로 `com.fasterxml.jackson.annotation`), `jakarta.*` 사용. Boot 2·3 예제를 그대로 옮기지 말고, 확실하지 않은 API는 먼저 확인한다.
- **Tailwind CSS 4**: `tailwind.config.js`·`@tailwind` 지시어를 쓰지 않는다. CSS에서 `@import "tailwindcss";`, 설정은 `@theme`, Vite 플러그인은 `@tailwindcss/vite`.
- **React 19 + Vite 8**: 함수 컴포넌트와 훅만 사용한다.
- Testcontainers·springdoc은 아직 `build.gradle`에 없다. 처음 필요한 기능에서 추가를 제안하고 확인을 받는다.

## 폴더 구조

```
hooklens/
├── backend/            Spring Boot (패키지: com.hooklens)
├── frontend/           React (2주차에 생성)
├── docker-compose.yml  MySQL, Redis
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

## 하지 말 것

- 새 의존성은 추가하기 전에 이유를 설명하고 확인을 받는다.
- `.env` 파일은 읽거나 수정하지 않는다. 변수 이름이 바뀌면 `.env.example`만 고친다.
- 3주차(REQ-AUTH-001) 전에는 Spring Security 의존성을 넣지 않는다 — 기본 로그인이 걸려 웹훅 수신 테스트가 막힌다.
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
- **request_log**: request_id(PK), endpoint_id(FK), method, path, query_string, headers(JSON), body(MEDIUMTEXT, 최대 1MB), content_type, ip, size_bytes, received_at — 인덱스 (endpoint_id, request_id DESC), (received_at)
- **response_rule**: rule_id(PK), endpoint_id(FK), priority, is_default, match_method, match_header, match_body_contains, status_code, response_headers(JSON), response_body, delay_ms(0~5000)
- 엔드포인트 삭제 시 요청 로그·응답 규칙 함께 삭제
- Redis: `refresh:{memberId}`(14일), `rate:{endpointId}:{yyyyMMddHHmm}`(2분), 채널 `hook-events`
  - Refresh 토큰은 회원당 1개만 저장 → 다른 기기에서 로그인하면 이전 기기는 재발급 시 로그아웃된다(의도한 단순화).

## 보관 정책 (REQ-LOG-005 스케줄러, 매일 03:00 KST)

- 회원 엔드포인트의 요청 로그: 30일 지난 것 삭제
- 비회원 엔드포인트: `expires_at`이 지난 엔드포인트를 로그·응답 규칙과 함께 삭제 (그 전까지는 수신 시 410)
- 대량 삭제는 한 번에 지우지 않고 1,000건씩 나눠 삭제한다(테이블 잠금 방지).

## 요구사항 (우선순위: P0 = MVP, P1 = 4주 범위, P2 = 이후)

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

## 일정

- 1주차: 세팅, 엔드포인트 발급, 웹훅 수신·저장, 요청 조회 API
- 2주차: SSE, 대시보드·상세 화면, 기본 응답 설정 → **MVP 완성**
- 3주차: 회원·JWT, 엔드포인트 관리, Rate Limit·크기 제한, 리플레이·SSRF 차단
- 4주차: Docker·EC2 배포, CI/CD, 로그 정리 스케줄러, README·회고 → **배포 완료**
