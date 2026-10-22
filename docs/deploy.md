# HookLens 배포 절차 (EC2 한 대 + Docker Compose)

```
인터넷 ──:80──▶ [nginx] ──▶ [backend] ──▶ [mysql]     ← docker-compose.prod.yml 하나로 같이 뜬다
                 화면·프록시    Spring Boot     데이터는 볼륨에 보관
```
- 밖으로 열린 포트는 **80(HTTP)** 과 접속용 **22(SSH)** 뿐이다. 백엔드(8080)·MySQL(3306)은 Docker 내부망에서만 보인다.
- 테이블은 MySQL 컨테이너가 처음 뜰 때 `deploy/mysql/init/01-schema.sql`로 만든다 (운영은 `ddl-auto: validate`).
- 로컬에서 같은 구성을 먼저 띄워 확인했다: `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build` → http://localhost

---

## 1. EC2 만들기 (AWS 콘솔)

1. 리전: 오른쪽 위에서 **아시아 태평양(서울) ap-northeast-2**
2. EC2 → **인스턴스 시작**
   - 이름: `hooklens`
   - AMI: **Ubuntu Server 24.04 LTS** (64비트 x86)
   - 인스턴스 유형: **t3.micro** (메모리 1GB → 아래 3단계에서 스왑 추가)
   - 키 페어: **새 키 페어 생성** → 이름 `hooklens`, RSA, `.pem` → 다운로드된 파일을 잘 보관 (다시 받을 수 없다)
   - 네트워크 설정 → 보안 그룹 생성
     - SSH(22): 소스 **내 IP** (아무나 접속 시도하지 못하게)
     - **HTTP(80) 허용**: 소스 0.0.0.0/0 (웹훅·브라우저가 들어오는 문)
   - 스토리지: **20GB** gp3 (기본 8GB는 Docker 이미지 빌드에 빠듯하다)
3. **인스턴스 시작** → 목록에서 **퍼블릭 IPv4 주소**를 복사해 둔다 (아래에서 `<공인IP>`)

> 공인 IP는 인스턴스를 **중지 → 시작**하면 바뀐다. 계속 같은 주소가 필요하면 **탄력적 IP**를 할당해 붙인다.
> 바뀌면 `.env.prod`의 `HOOKLENS_BASE_URL`과 깃허브 웹훅 주소도 바꿔야 한다.

## 2. 접속 (내 PC의 PowerShell)

```powershell
# .pem 파일이 있는 폴더에서. 처음 한 번: 키 파일을 나만 읽을 수 있게 (안 하면 ssh가 거부한다)
icacls .\hooklens.pem /inheritance:r /grant:r "$($env:USERNAME):R"

ssh -i .\hooklens.pem ubuntu@<공인IP>
```
처음 접속 때 `Are you sure you want to continue connecting?` → `yes`

## 3. 서버 준비 (EC2 안에서, 처음 한 번)

```bash
# 스왑 2GB: 메모리(1GB)가 모자랄 때 디스크를 대신 쓴다. 특히 이미지 빌드(Gradle) 중에 필요
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅해도 유지

# 서버 시간대 (컨테이너는 TZ로 이미 서울이지만, 서버 로그도 맞춘다)
sudo timedatectl set-timezone Asia/Seoul

# Docker 설치 (공식 설치 스크립트) + sudo 없이 docker 쓰기
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit   # 그룹 변경을 적용하려고 나갔다가 다시 ssh 접속
```

## 4. 코드 받고 설정하기

```bash
git clone https://github.com/songjeongseon1/hooklens.git
cd hooklens

cp .env.prod.example .env.prod
openssl rand -base64 24   # 비밀번호로 쓸 무작위 문자열 (두 번 실행해 DB_PASSWORD, DB_ROOT_PASSWORD에)
nano .env.prod            # 비밀번호 두 개와 HOOKLENS_BASE_URL=http://<공인IP> 를 고치고 Ctrl+O, Enter, Ctrl+X
```

## 5. 실행

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```
- 처음에는 이미지를 서버에서 빌드해서 **5~10분** 걸린다 (t3.micro).
- 상태 확인: `docker compose -p hooklens-prod ps` → mysql `healthy`, backend·nginx `Up`
- 백엔드 로그: `docker compose -p hooklens-prod logs -f backend` → `Started HookLensApplication`이 보이면 성공 (Ctrl+C로 나가기)
- 브라우저에서 **http://<공인IP>** 접속

## 6. 실제 웹훅으로 확인 (깃허브)
1. 화면에서 엔드포인트 만들기 → URL 복사
2. 깃허브 저장소 → Settings → Webhooks → Add webhook → Payload URL에 붙여 넣기, Content type `application/json` → Add
3. 깃허브가 바로 `ping` 이벤트를 보낸다 → HookLens 화면에 실시간으로 뜬다

## 코드를 고친 뒤 다시 배포
```bash
cd ~/hooklens && git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```
바뀐 부분만 다시 빌드한다(레이어 캐시). DB 데이터는 볼륨에 있어서 그대로 남는다.

## 문제가 생기면
| 증상 | 확인 |
| --- | --- |
| 브라우저가 접속 자체를 못 함 | 보안 그룹에 HTTP(80)가 열렸는지, `docker compose -p hooklens-prod ps`에 nginx가 Up인지 |
| 502 Bad Gateway | 백엔드가 아직 뜨는 중이거나 실패 → `logs backend` |
| 백엔드 로그에 `Schema-validation` | 엔티티와 테이블이 다르다 → `01-schema.sql`을 엔티티 기준으로 다시 생성 |
| 빌드 중 멈추거나 `Killed` | 메모리 부족 → 스왑이 켜졌는지 `free -h` |
| 실시간이 늦게 뜬다 | `frontend/nginx.conf`의 stream location 주소 오타 (`proxy_buffering off`가 적용 안 됨) |
| 시간이 9시간 어긋남 | 컨테이너 `TZ`, 백엔드 `-Duser.timezone` 확인 |

## 비용
- t3.micro·스토리지 20GB는 프리 티어(가입 후 12개월) 대상이면 무료 범위다. 공인 IPv4는 시간당 요금이 붙을 수 있다 — 결제 대시보드에서 확인.
- 안 쓸 때는 인스턴스를 **중지**하면 컴퓨팅 요금이 멈춘다(스토리지는 계속). 완전히 정리하려면 **종료**(데이터 삭제).
