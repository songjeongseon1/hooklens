-- HookLens 운영 DB 초기 테이블 (1차 MVP)
--
-- MySQL 컨테이너는 데이터 폴더가 비어 있을 때(= 처음 시작할 때)만 /docker-entrypoint-initdb.d 안의 .sql을 순서대로 실행한다.
-- 이후 재시작에서는 실행되지 않는다 → 데이터가 지워지지 않는다.
--
-- 운영은 ddl-auto: validate라 앱이 테이블을 만들지 않고 "엔티티와 같은지"만 검사한다.
-- 그래서 손으로 쓰지 않고 JPA 엔티티에서 Hibernate로 자동 생성했다(컬럼 하나만 달라도 앱이 안 뜬다).
-- 엔티티를 바꾸면 이 파일도 다시 만들어야 한다. 2차에서는 Flyway로 변경 이력을 버전별로 관리한다.

SET NAMES utf8mb4; -- 이 파일을 읽는 연결의 문자셋 (한글 주석·데이터 안전)

-- 엔드포인트: 웹훅을 받는 고유 주소 하나 (REQ-EP-001)
create table endpoint (
    created_at datetime(6) not null,
    endpoint_id bigint not null auto_increment,
    expires_at datetime(6),                 -- 비회원은 발급 24시간 후, 회원은 null(만료 없음)
    member_id bigint,                       -- null = 비회원 (member 테이블은 2차)
    slug varchar(10) not null,              -- /h/{slug} 의 영숫자 10자
    name varchar(50),
    anonymous_token_hash varchar(64),       -- 익명 토큰의 SHA-256 hex (원문은 저장하지 않음)
    forward_url varchar(2048),              -- 자동 포워딩(P2)용, 1차에서는 쓰지 않음
    primary key (endpoint_id)
) engine=InnoDB;

-- 요청 로그: 엔드포인트로 들어온 웹훅 요청 하나 (REQ-HOOK-001)
create table request_log (
    size_bytes integer not null,
    endpoint_id bigint not null,
    received_at datetime(6) not null,
    request_id bigint not null auto_increment,
    method varchar(10) not null,
    ip varchar(45) not null,                -- IPv6까지
    path varchar(2048) not null,
    body mediumtext,                        -- 최대 16MB (1MB 제한은 P1)
    content_type varchar(255),
    headers json not null,                  -- {"이름": ["값", ...]}
    query_string text,
    primary key (request_id)
) engine=InnoDB;

-- 응답 규칙: 웹훅에 돌려줄 응답 설정 (REQ-HOOK-002, REQ-RES-001). 1차는 엔드포인트당 기본 규칙 하나
create table response_rule (
    delay_ms integer not null,
    is_default bit not null,
    status_code integer not null,
    endpoint_id bigint not null,
    rule_id bigint not null auto_increment,
    response_body text,
    response_headers json,                  -- {"이름": "값"}
    primary key (rule_id)
) engine=InnoDB;

-- slug는 겹치면 안 된다 (발급 시 재시도 + 이 제약이 최종 보장)
alter table endpoint
    add constraint uk_endpoint_slug unique (slug);

-- 엔드포인트별 최신순 커서 조회(REQ-LOG-002): WHERE endpoint_id = ? AND request_id < ? ORDER BY request_id DESC
create index idx_request_log_endpoint
    on request_log (endpoint_id, request_id desc);

-- 보관 기간 지난 로그 삭제(REQ-LOG-005, P1)
create index idx_request_log_received
    on request_log (received_at);

-- 엔드포인트가 지워지면 DB가 로그·응답 규칙도 함께 지운다 (@OnDelete(CASCADE))
alter table request_log
    add constraint fk_request_log_endpoint
    foreign key (endpoint_id) references endpoint (endpoint_id)
    on delete cascade;

alter table response_rule
    add constraint fk_response_rule_endpoint
    foreign key (endpoint_id) references endpoint (endpoint_id)
    on delete cascade;
