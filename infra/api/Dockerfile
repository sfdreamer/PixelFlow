# API 镜像：多阶段构建，构建上下文是仓库根目录（见 docker-compose.yml）。
# 依赖层与源码层分开，改代码不必重新下载 Maven 依赖。
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY backend/pom.xml ./
COPY backend/mvnw backend/mvnw.cmd ./
COPY backend/.mvn ./.mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY backend/src ./src
# 镜像构建不跑测试：测试需要隔离的 MySQL 与独立数据库，由 CI 的 backend job 执行 ./mvnw -B verify。
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime

# curl 仅用于容器健康检查；镜像内不安装任意外部工具。
RUN apk add --no-cache curl \
    && addgroup -S pixelflow \
    && adduser -S pixelflow -G pixelflow

WORKDIR /app
COPY --from=build /workspace/target/pixelflow-api-1.0.0.jar /app/app.jar
RUN mkdir -p /data/storage && chown -R pixelflow:pixelflow /data

USER pixelflow
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
