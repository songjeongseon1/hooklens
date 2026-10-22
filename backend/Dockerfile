# 백엔드 이미지: Spring Boot jar를 만들어 실행한다 (2단계 빌드).
# 실행에는 JDK·소스가 필요 없다 → 최종 상자에는 JRE + jar만 (작고 안전).

# ---- 1단계: 빌드 (jar 만들기) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 빌드 설정만 먼저 복사해 라이브러리를 받아 둔다 → 소스만 바뀌면 이 층은 캐시 재사용 (레이어 캐시)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# -x test: 테스트는 Testcontainers(Docker 필요)라 이미지 빌드 중에는 못 돌린다 → PR 전에 로컬에서(2차엔 GitHub Actions에서)
RUN ./gradlew bootJar -x test --no-daemon

# ---- 2단계: 실행 (jar만 담은 가벼운 상자) ----
FROM eclipse-temurin:21-jre
# 시간대: EC2·컨테이너 기본값은 UTC라 빼먹으면 9시간 어긋난다 (CLAUDE.md 시간 규칙)
ENV TZ=Asia/Seoul
WORKDIR /app
# 1단계 상자에서 jar만 꺼내 온다 (JDK·소스는 버려진다)
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# MaxRAMPercentage=60: 자바 힙을 컨테이너에 준 메모리의 60%까지만 → 작은 EC2에서 메모리 폭주 방지
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
