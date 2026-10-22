# 프론트 이미지: React를 빌드해서 Nginx로 서비스한다 (2단계 빌드).
# 빌드 결과는 그냥 html·js·css라 실행할 때 Node가 필요 없다 → 최종 상자에는 Nginx + dist만 (약 74MB).

# ---- 1단계: 빌드 (React → html/js/css) ----
FROM node:22-alpine AS build
WORKDIR /app

# 라이브러리 목록만 먼저 복사 → 소스만 바뀌면 npm ci 결과를 캐시에서 재사용 (레이어 캐시)
COPY package.json package-lock.json ./
# npm ci: package-lock.json과 정확히 같은 버전으로 설치 (배포는 "확인한 것과 똑같은 버전"이어야 한다)
RUN npm ci

# 나머지 소스 (.dockerignore의 node_modules·dist는 빼고 — 윈도우용 node_modules가 섞이면 안 된다)
COPY . .
RUN npm run build

# ---- 2단계: Nginx로 서비스 ----
FROM nginx:1.27-alpine
# 화면 + API 프록시 + SSE 설정 (frontend/nginx.conf)
COPY nginx.conf /etc/nginx/conf.d/default.conf
# 1단계의 빌드 결과만 꺼내서 nginx.conf의 root 폴더에 넣는다
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
