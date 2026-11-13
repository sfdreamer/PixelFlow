#!/usr/bin/env bash
# PixelFlow 演示脚本（Linux / macOS / Git Bash）。
# 严格按 OpenAPI 发送请求，覆盖：会话 → 分类/模板 → 上传 → 成功任务 → 结果字节 → 历史
# → 三个阶段失败 → 三个长耗时任务 → 第二个 Cookie 罐验证隔离。
#
# 用法：
#   bash scripts/demo.sh
#   BASE_URL=http://localhost:3000 bash scripts/demo.sh
#   ORIGIN=http://localhost:3000 BASE_URL=http://localhost:3000 bash scripts/demo.sh
#
# 约定：
#   * Cookie 罐、下载的结果文件都放在 $DEMO_DIR（默认 .local/demo-run），脚本不删除任何目录。
#   * 需要展示失败模拟，因此 API 必须运行在 DEMO_CONTROLS_ENABLED=true（Compose 演示默认开启）。
#   * 不依赖真实 AI、支付或任何密钥；只需 curl 与 node（项目本身就要 node）。
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3000}"
ORIGIN="${ORIGIN:-http://localhost:3000}"
RUN_DIR="${DEMO_DIR:-.local/demo-run}"
SAMPLES="$(cd "$(dirname "$0")" && pwd)/sample-assets"

mkdir -p "$RUN_DIR"
JAR_A="$RUN_DIR/cookies-a.txt"
JAR_B="$RUN_DIR/cookies-b.txt"
: >"$JAR_A"
: >"$JAR_B"

step() { printf '\n=== %s ===\n' "$1"; }
fail() { printf '✗ %s\n' "$1" >&2; exit 1; }

# Git Bash 里的 curl 是原生 Windows 程序，不认 /d/... 形式的绝对路径；
# cygpath -m 转成 D:/... 后两端都能用（Linux/macOS 没有 cygpath，保持原样）。
to_native() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

# 从 stdin 的 JSON 里取字段，支持 a.b.c 与数组下标 list.0.id
jget() {
  node -e '
    let raw = "";
    process.stdin.on("data", (d) => (raw += d)).on("end", () => {
      let value = JSON.parse(raw);
      for (const key of process.argv[1].split(".")) {
        if (value === null || value === undefined) break;
        value = value[key];
      }
      process.stdout.write(value === undefined || value === null ? "" : String(value));
    });
  ' "$1"
}

# api METHOD PATH JAR [额外 curl 参数...]
api() {
  local method="$1" path="$2" jar="$3"
  shift 3
  curl -sS --noproxy '*' -X "$method" "$BASE_URL$path" \
    -b "$jar" -c "$jar" -H "Accept: application/json" "$@"
}

# 非安全方法必须带合法 Origin，否则后端返回 403 ORIGIN_NOT_ALLOWED。
json_call() {
  local method="$1" path="$2" jar="$3" body="${4:-}"
  if [ -n "$body" ]; then
    api "$method" "$path" "$jar" -H "Origin: $ORIGIN" -H 'Content-Type: application/json' -d "$body"
  else
    api "$method" "$path" "$jar" -H "Origin: $ORIGIN"
  fi
}

# poll JAR TASK_ID TIMEOUT_SECONDS → 输出终态 Task JSON
poll() {
  local jar="$1" id="$2" deadline=$((SECONDS + ${3:-90})) body="" status=""
  while [ "$SECONDS" -lt "$deadline" ]; do
    body="$(api GET "/api/v1/tasks/$id" "$jar")"
    status="$(printf '%s' "$body" | jget status)"
    case "$status" in
      SUCCEEDED | FAILED)
        printf '%s' "$body"
        return 0
        ;;
    esac
    sleep 1
  done
  fail "任务 $id 在 ${3:-90}s 内没有进入终态（最后状态：${status:-unknown}）"
}

# 校验结果 URL 真的返回可解码图片字节，而不是只看字段非空。
check_result_bytes() {
  local jar="$1" task_id="$2" out="$RUN_DIR/result-$2.png"
  curl -sS --noproxy '*' -b "$jar" -c "$jar" "$BASE_URL/api/v1/tasks/$task_id/result" -o "$(to_native "$out")"
  node -e '
    const fs = require("fs");
    const bytes = fs.readFileSync(process.argv[1]);
    const isPng = bytes.length > 1024 && bytes[0] === 0x89
      && bytes.toString("ascii", 1, 4) === "PNG";
    const isJpeg = bytes.length > 1024 && bytes[0] === 0xff && bytes[1] === 0xd8;
    if (!isPng && !isJpeg) {
      console.error("结果文件不是可解码的图片字节，长度=" + bytes.length);
      process.exit(1);
    }
    console.log("   结果文件 " + process.argv[1] + " 字节数=" + bytes.length
      + " 格式=" + (isPng ? "PNG" : "JPEG"));
  ' "$out"
}

step "0. 目标环境"
printf '   BASE_URL=%s\n   ORIGIN=%s\n   Cookie 罐目录=%s\n' "$BASE_URL" "$ORIGIN" "$RUN_DIR"
curl -sS --noproxy '*' -f "$BASE_URL/" -o /dev/null || fail "无法访问 $BASE_URL，请先启动前端（npm run dev 或 docker compose up）"

step "1. 建立匿名会话（Cookie 罐 A）"
SESSION_A="$(api POST /api/v1/session "$JAR_A" -H "Origin: $ORIGIN")"
printf '   身份：%s\n' "$(printf '%s' "$SESSION_A" | jget displayName)"
printf '   会话 Cookie 已写入 %s\n' "$JAR_A"

step "2. 读取分类与模板"
CATEGORIES="$(api GET /api/v1/categories "$JAR_A")"
CATEGORY_ID="$(printf '%s' "$CATEGORIES" | jget items.0.id)"
printf '   分类数：%s，首个分类：%s（%s）\n' \
  "$(printf '%s' "$CATEGORIES" | jget items.length)" \
  "$CATEGORY_ID" "$(printf '%s' "$CATEGORIES" | jget items.0.name)"

TEMPLATES="$(api GET "/api/v1/templates?categoryId=$CATEGORY_ID" "$JAR_A")"
TEMPLATE_ID="$(printf '%s' "$TEMPLATES" | jget items.0.id)"
TEMPLATE_DETAIL="$(api GET "/api/v1/templates/$TEMPLATE_ID" "$JAR_A")"
REQUIRED="$(printf '%s' "$TEMPLATE_DETAIL" | jget requiredImageCount)"
printf '   模板：%s 需要 %s 张图，接受 %s\n' \
  "$(printf '%s' "$TEMPLATE_DETAIL" | jget title)" "$REQUIRED" \
  "$(printf '%s' "$TEMPLATE_DETAIL" | jget acceptedMimeTypes)"

step "3. 上传 $REQUIRED 张素材（每张单次请求，返回 201）"
SAMPLE_FILES=("$SAMPLES/pet-sample.png" "$SAMPLES/travel-island-a.png" "$SAMPLES/travel-island-b.png")
ASSET_IDS=()
for index in $(seq 0 $((REQUIRED - 1))); do
  FILE="${SAMPLE_FILES[$index]}"
  [ -f "$FILE" ] || fail "缺少示例素材 $FILE"
  UPLOADED="$(api POST /api/v1/assets "$JAR_A" \
    -H "Origin: $ORIGIN" \
    -F "templateId=$TEMPLATE_ID" \
    -F "file=@$(to_native "$FILE");type=image/png")"
  ASSET_ID="$(printf '%s' "$UPLOADED" | jget id)"
  [ -n "$ASSET_ID" ] || fail "上传失败：$UPLOADED"
  ASSET_IDS+=("$ASSET_ID")
  printf '   上传 %s → assetId=%s\n' "$FILE" "$ASSET_ID"
done

ASSET_JSON="$(printf '"%s",' "${ASSET_IDS[@]}")"
ASSET_JSON="[${ASSET_JSON%,}]"

step "4. 创建成功任务（无 simulation，使用后端默认耗时）"
CREATED="$(json_call POST /api/v1/tasks "$JAR_A" \
  "{\"templateId\":\"$TEMPLATE_ID\",\"assetIds\":$ASSET_JSON}")"
TASK_ID="$(printf '%s' "$CREATED" | jget id)"
printf '   202 受理：taskId=%s status=%s revision=%s\n' \
  "$TASK_ID" "$(printf '%s' "$CREATED" | jget status)" "$(printf '%s' "$CREATED" | jget revision)"

TERMINAL="$(poll "$JAR_A" "$TASK_ID" 90)"
printf '   终态：status=%s currentStage=%s\n' \
  "$(printf '%s' "$TERMINAL" | jget status)" "$(printf '%s' "$TERMINAL" | jget currentStage)"
printf '%s' "$TERMINAL" | node -e '
  let raw = "";
  process.stdin.on("data", (d) => (raw += d)).on("end", () => {
    const task = JSON.parse(raw);
    for (const step of task.steps) {
      console.log("   " + step.stage.padEnd(12) + step.status);
    }
  });
'
[ "$(printf '%s' "$TERMINAL" | jget status)" = "SUCCEEDED" ] || fail "期望 SUCCEEDED，实际 $TERMINAL"

step "5. 校验结果 URL 返回真实图片字节"
check_result_bytes "$JAR_A" "$TASK_ID"

step "6. 查询个人历史"
HISTORY="$(api GET "/api/v1/tasks?limit=5" "$JAR_A")"
printf '%s' "$HISTORY" | node -e '
  let raw = "";
  process.stdin.on("data", (d) => (raw += d)).on("end", () => {
    const page = JSON.parse(raw);
    console.log("   本页 " + page.items.length + " 条，nextCursor=" + (page.nextCursor ?? "null"));
    for (const item of page.items) {
      console.log("   " + item.id + " " + item.status.padEnd(9) + " " + item.templateTitle);
    }
  });
'

step "7. 三个阶段各创建一次失败任务（failAt=ASSET_CHECK / GENERATE / FINALIZE）"
for STAGE in ASSET_CHECK GENERATE FINALIZE; do
  BODY="{\"templateId\":\"$TEMPLATE_ID\",\"assetIds\":$ASSET_JSON,\"simulation\":{\"failAt\":\"$STAGE\",\"stageDelayMs\":{\"ASSET_CHECK\":300,\"GENERATE\":300,\"FINALIZE\":300}}}"
  CREATE="$(json_call POST /api/v1/tasks "$JAR_A" "$BODY")"
  FAILED_TASK="$(printf '%s' "$CREATE" | jget id)"
  RESULT="$(poll "$JAR_A" "$FAILED_TASK" 60)"
  printf '%s' "$RESULT" | node -e '
    let raw = "";
    process.stdin.on("data", (d) => (raw += d)).on("end", () => {
      const task = JSON.parse(raw);
      const steps = task.steps.map((s) => s.stage + "=" + s.status).join(" ");
      console.log("   failAt=" + (process.argv[1] || "").padEnd(12)
        + " status=" + task.status
        + " error=" + (task.error ? task.error.code + "@" + task.error.failedStage : "null")
        + " result=" + (task.result === null ? "null" : "present"));
      console.log("     " + steps);
    });
  ' "$STAGE"
  [ "$(printf '%s' "$RESULT" | jget status)" = "FAILED" ] || fail "failAt=$STAGE 期望 FAILED"
  [ -z "$(printf '%s' "$RESULT" | jget result.previewUrl)" ] || fail "失败任务不应有结果"
done

step "8. 以每阶段 4 秒连续创建三个任务（观察排队与串行执行）"
LONG_IDS=()
for index in 1 2 3; do
  BODY="{\"templateId\":\"$TEMPLATE_ID\",\"assetIds\":$ASSET_JSON,\"simulation\":{\"failAt\":null,\"stageDelayMs\":{\"ASSET_CHECK\":4000,\"GENERATE\":4000,\"FINALIZE\":4000}}}"
  CREATE="$(json_call POST /api/v1/tasks "$JAR_A" "$BODY")"
  LONG_ID="$(printf '%s' "$CREATE" | jget id)"
  LONG_IDS+=("$LONG_ID")
  printf '   受理 task#%s=%s status=%s（约 12 秒后完成）\n' "$index" "$LONG_ID" \
    "$(printf '%s' "$CREATE" | jget status)"
done
printf '   3 秒后观察队列状态（应看到一个 RUNNING，其余 QUEUED）：\n'
sleep 3
for LONG_ID in "${LONG_IDS[@]}"; do
  SNAPSHOT="$(api GET "/api/v1/tasks/$LONG_ID" "$JAR_A")"
  printf '   %s status=%-9s currentStage=%s\n' "$LONG_ID" \
    "$(printf '%s' "$SNAPSHOT" | jget status)" "$(printf '%s' "$SNAPSHOT" | jget currentStage)"
done
printf '   等待全部完成：\n'
for LONG_ID in "${LONG_IDS[@]}"; do
  FINAL="$(poll "$JAR_A" "$LONG_ID" 120)"
  printf '   %s → %s\n' "$LONG_ID" "$(printf '%s' "$FINAL" | jget status)"
done

step "9. 第二个 Cookie 罐验证身份隔离"
SESSION_B="$(api POST /api/v1/session "$JAR_B" -H "Origin: $ORIGIN")"
printf '   身份 B：%s\n' "$(printf '%s' "$SESSION_B" | jget displayName)"
CODE_B="$(curl -sS --noproxy '*' -o /dev/null -w '%{http_code}' -b "$JAR_B" "$BASE_URL/api/v1/tasks/$TASK_ID")"
printf '   B 读取 A 的任务 → HTTP %s（期望 404）\n' "$CODE_B"
[ "$CODE_B" = "404" ] || fail "身份隔离失败：B 读到了 A 的任务（$CODE_B）"
HISTORY_B="$(api GET "/api/v1/tasks?limit=5" "$JAR_B")"
printf '   B 的历史条数：%s（期望 0）\n' "$(printf '%s' "$HISTORY_B" | jget items.length)"
[ "$(printf '%s' "$HISTORY_B" | jget items.length)" = "0" ] || fail "B 的历史不为空"

step "完成"
printf '   A 的任务总数应为 1 + 3 + 3 = 7，可在 http://localhost:3000/history 查看。\n'
printf '   手工补足的两步：刷新页面确认历史恢复；关闭页面再打开确认后台仍执行。\n'
printf '   本次产生的文件保留在 %s，脚本不做任何递归清理。\n' "$RUN_DIR"
