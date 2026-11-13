# PixelFlow · 模拟素材制作

选择模板 → 上传图片 → 提交任务 → Java 后台串行执行「素材检查 → 模拟生成 → 结果整理」三阶段 → 在个人历史里查看状态、失败原因或结果图片。没有真实 AI 调用、没有支付和账号体系，全部行为可复现、可解释。

本文档与 `docs/` 下的基线文档、`openapi.json`、`schema.sql` 一并对齐；`docs/验收记录.md` 记录每一条验收项的真实执行情况（含未执行项）。

## 1. 能力概览

| 能力 | 实现方式 |
|---|---|
| 模板库与详情 | Java 提供分类、模板、封面；前端 App Router 页面渲染，1 图 / 2 图模板动态生成上传位 |
| 匿名身份 | `POST /api/v1/session` 创建 256bit 随机 Cookie，数据库只存 SHA-256；素材、任务、结果全部按 owner 鉴权 |
| 任务受理 | `POST /api/v1/tasks` 同一事务写 task + 3 行 task_step + task_asset + 快照，提交后返回 `202` 与 `Location`，不等执行 |
| 后台执行 | 单线程调度器每 500ms 领取最早 QUEUED 任务，三阶段顺序执行；查询接口完全不参与推进 |
| 失败模拟 | 请求可带 `simulation.failAt` 与三阶段耗时；命中阶段失败、后续阶段 SKIPPED、不产生结果 |
| 查询与历史 | 游标分页（`created_at DESC, id DESC`），列表与详情共用同一 Task DTO；GET 不写库 |
| 重启收尾 | 启动时把遗留 QUEUED/RUNNING 标为 `EXECUTION_INTERRUPTED`，完成历史不动 |
| 前端状态机 | 上传位 `EMPTY/UPLOADING/READY/ERROR` + selectionVersion、提交态 `IDLE/SUBMITTING/UNCERTAIN`、每任务独立查询错误、2 秒递归轮询（≤4 并发） |

## 2. 实际使用版本

| 组件 | 版本 | 说明 |
|---|---|---|
| Node.js | 22.22.2 | `frontend/package.json` 声明 `>=22` |
| Next.js | 16.4.6 | App Router + Turbopack 构建 |
| React / React DOM | 19.3.1 | |
| TypeScript | 5.9.3 | `tsc --noEmit` 通过 |
| Vitest | 3.2.7 | 21 个前端测试 |
| ESLint | 9.38.0 + eslint-config-next 16.4.6 | 独立 CLI，不依赖 Next 的 lint 子命令 |
| Java | Temurin 21.0.11 LTS | `maven.compiler.release=21` |
| Spring Boot | 3.5.16 | web / validation / actuator |
| MyBatis starter | 3.0.5 | Mapper XML 位于 `resources/mappers` |
| Flyway | 随 Boot 3.5.16 管理 | 含 `flyway-mysql` |
| MySQL | 镜像固定 `mysql:8.4.6` | 本机验证实例为 MySQL 8.0.44（见 §14） |
| Maven Wrapper | 3.9.12 | 提交 `mvnw` / `mvnw.cmd` / `.mvn/wrapper` |

依赖版本全部固定在 `frontend/package-lock.json` 与 `backend/pom.xml`，未使用 `latest`。

## 3. 目录结构

```
frontend/                     Next.js App Router
  src/app/                    页面：/ 列表、/templates/[id] 详情、/history 历史
  src/features/catalog|upload|tasks|identity/
  src/lib/api/                types.ts（对齐 OpenAPI）、client.ts、endpoints.ts
  next.config.ts              /api/v1/** 同源转发到 JAVA_API_ORIGIN
backend/                      Spring Boot
  src/main/java/com/pixelflow/{common,identity,catalog,asset,task,execution,storage}
  src/main/resources/db/migration/V1__init_schema.sql、V2__seed_demo_data.sql
  src/main/resources/demo/*.png        打包的封面与固定结果素材
  src/main/resources/mappers/*.xml     MyBatis 映射
  src/test/java/com/pixelflow/*IT.java 真实 MySQL 集成测试
infra/api/Dockerfile          API 多阶段镜像
infra/web/Dockerfile          Web 多阶段镜像（JAVA_API_ORIGIN 作为 build arg）
docker-compose.yml            mysql → api(healthy) → web
.github/workflows/ci.yml      frontend / backend 两个 job
scripts/demo.sh、demo.ps1     OpenAPI 演示脚本（两种终端）
scripts/generate-demo-assets.py  自绘示例封面与结果图（Pillow）
docs/                         规格、计划、契约、验收记录
```

## 4. 快速启动

### 4.1 Compose（推荐）

需要 Docker Engine / Docker Desktop 已启动。

```bash
cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
docker compose up --build
```

- 浏览器打开 <http://localhost:3000>，只与 web 通信；`/api/v1/**` 由 Next.js 转发到 `http://api:8080`。
- 启动顺序由健康检查保证：mysql healthy → api readiness（含 DB 与启动收尾）→ web。
- 首次启动自动建表并写入种子数据（2 分类 / 4 模板），无需手工执行 SQL。
- 停止：`docker compose down`。**不要加 `-v`**：`mysql-data` 与 `api-storage` 两个卷保存数据库与用户素材。
- Windows 也可以直接双击 PowerShell 执行上面两条命令；仓库内命令在 Git Bash / PowerShell 中一致。

### 4.2 本机开发（不经容器）

```bash
# 1) 后端：需要一个可用的 MySQL 8.x 与一个空库
cd backend
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=pixelflow DB_USER=root DB_PASSWORD= \
STORAGE_ROOT=./.data/storage DEMO_CONTROLS_ENABLED=true \
./mvnw -B spring-boot:run

# 2) 前端（另开终端）
cd frontend
npm ci
JAVA_API_ORIGIN=http://localhost:8080 npm run dev
```

前端默认转发到 `http://localhost:8080`，因此不设 `JAVA_API_ORIGIN` 也能联调。
生产模式：`npm run build && npm start`（同样默认 3000 端口）。

### 4.3 健康检查

```bash
curl http://localhost:8080/actuator/health/readiness      # {"status":"UP"}
curl -i http://localhost:3000/api/v1/categories           # 公开接口，应返回 2 个分类
```

`readiness` 组包含 `db` 与 `startupRecoveryState`：数据库不可用或启动收尾未完成时不会是 UP。

## 5. 环境变量

复制 `.env.example` 即可，全部为非敏感演示值。私有数据库口令不得放进 `NEXT_PUBLIC_*`（本项目不使用任何 `NEXT_PUBLIC_` 变量）。

| 变量 | 默认 | 作用 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | mysql / 3306 / pixelflow / pixelflow / - | 数据源 |
| `STORAGE_ROOT` | `./.data/storage`（镜像内 `/data/storage`） | 上传与结果文件根目录，必须在卷内 |
| `SERVER_PORT` | 8080 | API 端口 |
| `ALLOWED_ORIGINS` | `http://localhost:3000` | 允许发起非安全方法的来源，逗号分隔 |
| `COOKIE_SECURE` | false | HTTPS 部署置 true；本地 HTTP 必须 false |
| `DEMO_CONTROLS_ENABLED` | false（Compose 演示 true） | 是否接受 `simulation`；false 时返回 403 |
| `STAGE_DELAY_ASSET_CHECK_MS` / `..._GENERATE_MS` / `..._FINALIZE_MS` | 2000 | 三阶段默认耗时，可被单个任务覆盖 |
| `WORKER_FIXED_DELAY_MS` | 500 | 调度器空闲重试间隔 |
| `JAVA_API_ORIGIN` | `http://localhost:8080` | Next.js 转发目标（容器内为 `http://api:8080`） |
| `WORKER_AUTO_START` | true | 启动收尾后是否放开调度闸门；集成测试置 false |

## 6. 连接拓扑

```
浏览器 ──HTTP──> web(Next.js :3000) ──转发 /api/v1/**──> api(Spring Boot :8080) ──> mysql(:3306)
                        │                                        │
                        └── 页面与静态资源                        └── 持久卷 /data/storage
```

- 浏览器只认识一个源（`localhost:3000`），Cookie 天然同源，`Set-Cookie`、状态码、multipart 原样透传。
- 所有媒体 URL 都是 `/api/v1/...` 相对路径，不会把 `http://api:8080` 暴露给浏览器。
- 数据库与存储卷不对外发布端口（Compose 中未映射 mysql/api 端口）。

## 7. 迁移与种子素材

- `V1__init_schema.sql`：9 张表（UTF8MB4、InnoDB、UTC `DATETIME(3)`、CHECK 约束、无级联删除）。
- `V2__seed_demo_data.sql`：固定 UUID 的 2 个分类、4 个模板（`…101`/`…102` 单图，`…201`/`…202` 双图），可重复执行不会重复插入。
- 封面与固定结果图打包在 `resources/demo/*.png`，由 `scripts/generate-demo-assets.py` 自绘生成（见 §18）。
- 已应用的迁移不修改；结构变化一律新增 `V3__…`。

## 8. 身份与隔离

1. 前端首次进入调用 `POST /api/v1/session`；没有有效 Cookie 时创建 `app_user` + `user_session`，有则返回原用户。
2. Cookie `pf_session`：256bit 随机、`HttpOnly`、`SameSite=Strict`、`Path=/api`、30 天；HTTPS 下加 `Secure`；数据库只存 SHA-256，不存明文，不滑动续期。
3. 私有接口只从会话解析 `userId`，从不接受请求里的 `userId`；访问他人资源返回 404（不泄露存在性），无会话返回 401。
4. 私有响应 `Cache-Control: no-store`；非安全方法校验 `Origin`，不匹配返回 403 `ORIGIN_NOT_ALLOWED`。
5. 401 时前端停止全部轮询并提示「会话已失效」，由用户点「重新建立会话」创建新身份，旧任务不会挂到新身份上。

演示隔离：普通窗口 A 与隐私窗口 B 各建任务，历史互不可见；B 访问 A 的素材/任务/结果均为 404。清除 Cookie 即丢失原匿名身份，这是明确限制，不提供找回。

## 9. 接口与状态机

基础路径 `/api/v1`，字段与状态码以 `docs/openapi.json` 为准。

| 方法 | 路径 | 授权 | 说明 |
|---|---|---|---|
| POST | `/session` | 可无 Cookie | 建立/恢复身份，200 |
| GET | `/session` | 必须 | 当前身份，过期 401 |
| GET | `/categories` | 公开 | 分类列表 |
| GET | `/templates?categoryId=` | 公开 | 启用模板 |
| GET | `/templates/{id}`、`/cover` | 公开 | 详情、封面流 |
| POST | `/assets` | 必须 | 单文件上传，201 |
| GET | `/assets/{id}/content` | 必须 | 本人素材预览 |
| POST | `/tasks` | 必须 | 202 + Location |
| GET | `/tasks?limit=&cursor=&templateId=` | 必须 | 本人历史（默认 20，最大 100） |
| GET | `/tasks/{id}` | 必须 | 只读快照 |
| GET | `/tasks/{id}/result` | 必须 | 成功任务结果流 |

状态：`QUEUED → RUNNING → SUCCEEDED | FAILED`，终态不可回退；阶段 `ASSET_CHECK → GENERATE → FINALIZE`，阶段态 `PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED`。每次可见迁移递增 `revision`，GET 不写 `updated_at`。

错误统一为 `{"code","message","traceId","details"}`：400 格式、401 未认证、403 模拟控制禁用或来源拒绝、404 不存在或非本人、413 过大、415 类型不支持、422 业务规则、500 未预期、503 依赖故障。文件流带服务端检测的 `Content-Type` 与 `nosniff`。

## 10. 演示

### 10.1 脚本（推荐）

```bash
bash scripts/demo.sh                                  # Linux / macOS / Git Bash
powershell -ExecutionPolicy Bypass -File scripts\demo.ps1   # Windows PowerShell
```

两个脚本行为一致，覆盖：建立会话 → 分类/模板 → 上传 N 张 → 成功任务 → **校验结果 URL 返回真实图片字节** → 历史 → 三个阶段各失败一次 → 三个长耗时任务观察排队 → 第二个 Cookie 罐验证隔离。Cookie 罐与下载的结果文件落在 `.local/demo-run/`，脚本不做任何递归清理。

前提：`DEMO_CONTROLS_ENABLED=true`（Compose 演示默认开启），目标地址默认 `http://localhost:3000`，可用 `BASE_URL` 覆盖。

### 10.2 手工演示三段失败

```bash
# 1) 会话
curl -s -c cookies.txt -H 'Origin: http://localhost:3000' -X POST http://localhost:3000/api/v1/session

# 2) 上传（返回 id 即 assetId）
curl -s -b cookies.txt -H 'Origin: http://localhost:3000' \
  -F templateId=00000000-0000-4000-8000-000000000101 \
  -F file=@scripts/sample-assets/pet-sample.png http://localhost:3000/api/v1/assets

# 3) 指定在 GENERATE 阶段失败
curl -s -b cookies.txt -H 'Origin: http://localhost:3000' -H 'Content-Type: application/json' \
  -d '{"templateId":"00000000-0000-4000-8000-000000000101","assetIds":["<assetId>"],
       "simulation":{"failAt":"GENERATE","stageDelayMs":{"ASSET_CHECK":1000,"GENERATE":2000,"FINALIZE":1000}}}' \
  http://localhost:3000/api/v1/tasks
```

结果：`ASSET_CHECK=SUCCEEDED`、`GENERATE=FAILED(SIMULATED_FAILURE)`、`FINALIZE=SKIPPED`、`result=null`。把 `failAt` 换成 `ASSET_CHECK` / `FINALIZE` 即为另外两段。

### 10.3 耗时与多任务

- 单个任务的三阶段耗时由 `simulation.stageDelayMs` 覆盖（0—30000ms），未传的阶段用后端默认值（`STAGE_DELAY_*`）。快照入 `task.simulation_snapshot`，改配置不影响已受理任务。
- 连续创建三个任务即可看到「一个 RUNNING、其余 QUEUED」：调度器串行执行，按 `created_at, id` 领取。
- 受理后立即解锁按钮；任务执行与页面无关，关闭页面/刷新后历史从数据库恢复并继续跟踪未完成任务。

## 11. 测试与 CI

```bash
# 前端
cd frontend && npm run lint && npm run typecheck && npm run test -- --run && npm run build

# 后端：单元测试 + 真实 MySQL 集成测试 + 打包
cd backend
PF_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/pixelflow_it?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false' \
PF_TEST_MYSQL_USER=root PF_TEST_MYSQL_PASSWORD= \
./mvnw -B verify
```

- 前端 21 个测试：`TaskTracker`（轮询/并发上限/轮转/404 暂停/401 停止/revision 合并/stop 后丢弃）、`useUploadSlots`（替换、移除、旧响应）、`TaskCard`、模板详情页、`useTaskFeed`（401 不循环重载）。
- 后端 11 个单元测试 + 20 个集成测试：B01—B13 覆盖受理、三阶段、失败模拟、只读查询、串行顺序、校验、隔离、文件失败、并发领取、事务回滚、启动收尾、游标分页、关闭模拟控制。
- 集成测试使用真实 MySQL（不是 H2），每类测试前清空运行表并关闭调度闸门，避免共享 schema 时互相抢任务。
- CI：`.github/workflows/ci.yml`，push 与 PR 触发，`frontend`（npm ci / lint / typecheck / test / build）与 `backend`（`./mvnw -B verify`，MySQL 8.4.6 service 容器）两个 job。

## 12. 重启与中断策略

- 启动顺序：Flyway 迁移 → 启动收尾（遗留 QUEUED/RUNNING → `FAILED` + `EXECUTION_INTERRUPTED`）→ readiness UP → 放开调度闸门。
- QUEUED 任务三个阶段改 `SKIPPED`、`failedStage=null`；RUNNING 任务保留已完成阶段，运行阶段改 `FAILED`，其余 `SKIPPED`；已完成任务不动。
- **不续跑**：进程被杀后任务就是中断失败，需要用户重新提交。这是本方案的范围决定，不会伪装成断点恢复。
- **仅单实例**：启动收尾会中断其他实例正在跑的任务，因此 Compose 明确不扩容 API。
- 数据库运行中不可用：停止该任务后续步骤、不宣告成功、记录 `taskId`/阶段/`traceId`，本轮调度暂停；恢复数据库并重启 API 后由启动收尾结束这些任务。

## 13. 已完成 / 未完成

已完成（源码实现 + 本地运行验证）：模板库/详情/历史三页、1/2 图上传位与前后端双端校验、匿名身份与隔离、任务受理与三阶段执行、三类失败模拟、结果预览、游标分页历史、启动收尾、前端轮询与错误分层、演示脚本、Compose 与 CI 配置。

未完成（明确不在本次范围）：真实 AI 生成、支付/会员、账号注册与找回身份、模板管理后台、任务下载/删除/重试、消息队列与多实例、断点续跑、E2E 自动化纳入 CI。

## 14. 已知限制（务必阅读）

1. **容器未在本机验证**：开发环境没有可用的 Docker 引擎（Docker Desktop 未运行，`wsl`/`sc` 被安全策略阻止），因此 `docker compose up --build` 未执行过；Compose 文件、两个 Dockerfile 与健康检查顺序只做了静态核对，`docs/验收记录.md` 的 A01 标注为「未验证」。
2. **数据库版本**：镜像固定 `mysql:8.4.6`；本地集成测试与联调跑的是本机独立实例 **MySQL 8.0.44**（127.0.0.1:3307，数据目录 `.local/mysql-data`，未触碰机器上已有的 3306 实例与数据）。8.0 与 8.4 在本次使用的 DDL/JSON/CHECK 特性上一致，但 8.4 未经实测。
3. 匿名身份不可找回；同一浏览器清 Cookie 即新用户。
4. 串行执行：多任务排队，单任务吞吐受阶段耗时限制。
5. 孤立素材不自动清理：替换/移除只解除前端引用，后端文件与其数据库记录保留，演示存储会持续增长。
6. 任务执行中重启 → 该任务中断失败，不续跑。
7. 上传大小上限 5 MiB、20 百万像素；仅 JPEG/PNG。
8. 没有 CSRF token，依赖 `SameSite=Strict` + Origin 校验；跨站场景需要额外机制。
9. 日志与健康端点不输出环境变量；错误堆栈只写服务端日志。

### 提交前复核补充（2026-11-13）

本次源码复核发现以下前端边界问题，当前提交尚未修复，不代表全部验收通过：

- 历史请求在离页后返回时，可能通过 `tracker.sync()` 重新启动轮询。
- 轮询请求进行中提交新任务或刷新历史，可能安排重叠轮询并突破声明的并发上限。
- 快速切换模板分类时，旧请求晚返回可能覆盖当前分类的模板列表。
- 上传清理逻辑捕获了旧控制器数组，模板加载后实际上传请求可能无法在离页时取消。

这些问题来自源码审查，尚未运行复现。此前测试与验收结果保留为历史记录；此次提交准备阶段未重新运行构建或测试。Docker 引擎不可用，容器运行验收仍待完成。

## 15. 实际投入与取舍

- 本次实现由 AI 编码代理在单个连续会话内完成，**没有可信的人工工时记录**，因此不填估算小时数。可核对的规模：后端主代码 65 个 Java 文件约 3.6k 行 + 7 个 Mapper XML，后端测试 11 个文件约 1.1k 行，前端 22 个 TS/TSX 文件约 2.5k 行 + 9 个 CSS Module。
- 主要取舍：
  - 用「单线程调度器 + MySQL 队列」而不是 MQ/Redis —— 需求只要可观察的串行排队，少一个基础设施更可复现。
  - 结果图用模板预置素材复制，不做真实图像处理 —— 原题范围内「模拟生成」，避免引入真实 AI 依赖。
  - 轮询而非 SSE/WebSocket —— 无额外连接服务，容易用 fake timer 测试。
  - 启动收尾把中断任务置失败，而不是恢复执行 —— 明确不实现租约与阶段幂等。
  - 前端不引入组件库，用 CSS Modules + 变量，页面少、可读性优先。

## 16. 部署与回滚

```bash
# 1) 校验配置与卷位置
docker compose config
docker volume ls | grep pixelflow

# 2) 备份数据库与素材（示例：导出到宿主机当前目录）
docker compose exec -T mysql mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" pixelflow > pixelflow-$(date +%Y%m%d).sql
docker run --rm -v pixelflow_api-storage:/data -v "$PWD":/backup alpine tar czf /backup/storage-$(date +%Y%m%d).tgz -C /data .

# 3) 构建并启动
docker compose build --pull
docker compose up -d

# 4) 烟测
curl -fsS http://localhost:3000/api/v1/categories >/dev/null && echo ok
bash scripts/demo.sh
```

回滚：优先把 `infra/api`、`infra/web` 回退到上一个已验证镜像/提交后 `docker compose up -d`；**不要**直接执行破坏性降级 SQL，含不兼容迁移时按已验证的备份恢复数据库与存储卷。停止服务用 `docker compose down`，不要删除卷。

## 17. AI 工作流（本次如何使用 AI）

1. 读取 `docs/` 的规格、计划、OpenAPI、schema，先冻结契约（版本、枚举、错误码、状态机）再写代码。
2. 按 P0→P4 切片推进：骨架与迁移 → 身份/目录/上传 → 任务与执行 → 前端闭环 → 测试与交付；每片先用真实 HTTP 演示，再接页面。
3. 每次改动都跑对应测试：后端 `./mvnw -B verify`（真实 MySQL）、前端 lint/typecheck/test/build；失败先修根因，不调低断言。
4. 关键行为用真实环境复核：curl 走完整链路、检查结果 PNG 可解码、Playwright 跑页面与 401 会话失效路径。
5. 未验证项如实标注（容器、8.4、CI 远端执行），不用「应该可以」代替结果。

## 18. 素材与许可

- `backend/src/main/resources/demo/*.png`、`scripts/sample-assets/*.png` 全部由 `scripts/generate-demo-assets.py` 用 Pillow 程序化绘制（渐变背景 + 几何构图 + 文字），无第三方图片、无内网或付费资源。
- 这些素材随仓库以 CC0 1.0 释出，可自由复用；生成脚本本身也随仓库提供，便于复现或替换。
- 字体使用系统默认无衬线字体渲染，不打包第三方字体文件。
