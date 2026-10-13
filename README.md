# HookLens

외부 서비스가 보내는 웹훅을 고유 URL로 받아 실시간으로 보여주고, 원하는 응답을 돌려주는 개발자 도구입니다.

## 로컬 실행

1. `.env.example`을 복사해 `.env`를 만들고 비밀번호를 바꿉니다.
2. 루트에서 MySQL·Redis 실행: `docker compose up -d`
3. IntelliJ에서 `backend` 폴더를 Gradle 프로젝트로 엽니다. `local` 프로필이 루트의 `.env`를 자동으로 읽으므로 환경 변수를 따로 넣지 않아도 됩니다. (실행 설정의 Working directory가 `backend`여야 합니다.)
4. `HookLensApplication` 실행 후 http://localhost:8080/actuator/health 에서 `{"status":"UP"}` 확인

## 설계 결정

- **비밀 값은 환경 변수로만 주입**: 이전 팀 프로젝트에서 비밀 키 관리의 중요성을 겪어서, 처음부터 `.env`를 Git에서 제외하고 `application.yml`에는 변수 이름만 둡니다.
- **MySQL 포트 3307**: PC에 설치된 MySQL(3306)과 충돌하지 않게 했습니다.
- **Spring Security는 3주차에 추가**: 먼저 넣으면 모든 요청에 기본 로그인이 걸려 웹훅 수신 테스트가 막힙니다.
- **공통 응답·에러 코드**: 관리 API는 `{ success, data, error }` 형식으로 통일하고, 에러 코드는 API 명세서와 `ErrorCode` enum을 1:1로 맞춥니다.
