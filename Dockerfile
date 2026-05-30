# syntax=docker/dockerfile:1

# ---------- 构建：前端 + Quarkus 一体包 ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

COPY mvnw mvnw.cmd .mvn pom.xml ./
COPY frontend ./frontend
COPY src ./src

RUN chmod +x mvnw \
    && ./mvnw -B package -DskipTests -Dquarkus.package.jar.enabled=true

# ---------- 运行 ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r plantops --gid=10001 \
    && useradd -r -g plantops --uid=10001 --home-dir=/app plantops \
    && mkdir -p /app/data \
    && chown -R plantops:plantops /app

COPY --from=build --chown=plantops:plantops /build/target/quarkus-app ./

ENV QUARKUS_PROFILE=docker
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/q/health/ready || exit 1

USER plantops

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
