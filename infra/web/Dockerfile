# Web 镜像：多阶段构建，构建上下文是仓库根目录（见 docker-compose.yml）。
# Next.js 的 rewrites 在构建期写入路由清单，因此 JAVA_API_ORIGIN 必须作为构建参数传入；
# 运行期该变量只用于排查与服务端日志，不会改变已生成的路由。
FROM node:22-alpine AS deps

WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build

WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
ARG JAVA_API_ORIGIN=http://api:8080
ENV JAVA_API_ORIGIN=${JAVA_API_ORIGIN}
COPY --from=deps /app/node_modules ./node_modules
COPY frontend/ ./
# 前端构建不依赖运行中的 API：页面数据全部在运行期加载。
RUN npm run build

FROM node:22-alpine AS runtime

WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000
ARG JAVA_API_ORIGIN=http://api:8080
ENV JAVA_API_ORIGIN=${JAVA_API_ORIGIN}

COPY --from=build /app/package.json /app/package-lock.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/.next ./.next
COPY --from=build /app/next.config.ts ./next.config.ts

USER node
EXPOSE 3000

# 健康检查由 compose 定义（node -e fetch），镜像内不额外装工具。
CMD ["npm", "start"]
