# PixelFlow 演示脚本（Windows PowerShell / PowerShell 7）。
# 与 scripts/demo.sh 行为一致：会话 → 分类/模板 → 上传 → 成功任务 → 结果字节 → 历史
# → 三个阶段失败 → 三个长耗时任务 → 第二个 Cookie 罐验证隔离。
#
# 用法（Windows 10/11 自带 curl.exe）：
#   powershell -ExecutionPolicy Bypass -File scripts\demo.ps1
#   $env:BASE_URL='http://localhost:3000'; .\scripts\demo.ps1
#
# 约定：Cookie 罐与结果文件放在 .local\demo-run，脚本不删除任何目录；
# 需要展示失败模拟，API 必须运行在 DEMO_CONTROLS_ENABLED=true。

$ErrorActionPreference = 'Stop'

$BaseUrl = if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:3000' }
$Origin = if ($env:ORIGIN) { $env:ORIGIN } else { 'http://localhost:3000' }
$RunDir = if ($env:DEMO_DIR) { $env:DEMO_DIR } else { '.local\demo-run' }
$Samples = Join-Path $PSScriptRoot 'sample-assets'

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
$JarA = Join-Path $RunDir 'cookies-a.txt'
$JarB = Join-Path $RunDir 'cookies-b.txt'
foreach ($jar in @($JarA, $JarB)) {
    if (Test-Path $jar) { Remove-Item $jar -Force }
    New-Item -ItemType File -Force -Path $jar | Out-Null
}

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host "=== $Text ==="
}

function Stop-Demo([string]$Text) {
    Write-Host "x $Text" -ForegroundColor Red
    exit 1
}

# 统一入口：所有请求都带同一个 Cookie 罐，非安全方法由调用方补 Origin 头。
# 响应正文先落到文件再按 UTF-8 读取：Windows PowerShell 5.1 会按本地代码页解码原生命令
# 的标准输出，直接捕获 curl 的 stdout 会把中文 JSON 解坏。
$ResponseFile = Join-Path $RunDir '.response.json'
$BodyFile = Join-Path $RunDir '.request.json'

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Jar,
        [string[]]$Extra = @()
    )
    $curlArgs = @('-sS', '--noproxy', '*', '-X', $Method, "$BaseUrl$Path",
        '-b', $Jar, '-c', $Jar, '-H', 'Accept: application/json',
        '-o', $ResponseFile) + $Extra
    & curl.exe @curlArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { Stop-Demo "请求失败：$Method $Path" }
    if (-not (Test-Path $ResponseFile)) { return '' }
    return (Get-Content -LiteralPath $ResponseFile -Raw -Encoding UTF8)
}

function Invoke-Json {
    param([string]$Method, [string]$Path, [string]$Jar, [string]$Body = '')
    $extra = @('-H', "Origin: $Origin")
    if ($Body) {
        # Windows PowerShell 5.1 传参时会吞掉双引号，直接把 JSON 放在 -d 参数里会变成非法 JSON。
        # 写入文件后用 --data-binary @file 发送，两端一致且不丢字符。
        [System.IO.File]::WriteAllText($BodyFile, $Body, (New-Object System.Text.UTF8Encoding($false)))
        $extra += @('-H', 'Content-Type: application/json', '--data-binary', "@$BodyFile")
    }
    return Invoke-Api -Method $Method -Path $Path -Jar $Jar -Extra $extra
}

function Wait-Task {
    param([string]$Jar, [string]$TaskId, [int]$TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $task = (Invoke-Api -Method GET -Path "/api/v1/tasks/$TaskId" -Jar $Jar) | ConvertFrom-Json
        if ($task.status -eq 'SUCCEEDED' -or $task.status -eq 'FAILED') { return $task }
        Start-Sleep -Seconds 1
    }
    Stop-Demo "任务 $TaskId 在 $TimeoutSeconds 秒内没有进入终态"
}

function Show-Steps($Task) {
    foreach ($step in $Task.steps) {
        Write-Host ("   {0,-12}{1}" -f $step.stage, $step.status)
    }
}

function Test-ResultBytes {
    param([string]$Jar, [string]$TaskId)
    $out = Join-Path $RunDir "result-$TaskId.png"
    & curl.exe -sS --noproxy '*' -b $Jar -c $Jar "$BaseUrl/api/v1/tasks/$TaskId/result" -o $out
    if ($LASTEXITCODE -ne 0) { Stop-Demo '下载结果失败' }
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $out))
    $isPng = $bytes.Length -gt 1024 -and $bytes[0] -eq 0x89 -and
        [System.Text.Encoding]::ASCII.GetString($bytes, 1, 3) -eq 'PNG'
    $isJpeg = $bytes.Length -gt 1024 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xD8
    if (-not ($isPng -or $isJpeg)) {
        Stop-Demo "结果文件不是可解码图片，长度=$($bytes.Length)"
    }
    Write-Host ("   结果文件 {0} 字节数={1} 格式={2}" -f $out, $bytes.Length, $(if ($isPng) { 'PNG' } else { 'JPEG' }))
}

Write-Step '0. 目标环境'
Write-Host "   BASE_URL=$BaseUrl"
Write-Host "   ORIGIN=$Origin"
Write-Host "   Cookie 罐目录=$RunDir"
& curl.exe -sS --noproxy '*' -f "$BaseUrl/" -o NUL
if ($LASTEXITCODE -ne 0) { Stop-Demo "无法访问 $BaseUrl，请先启动前端" }

Write-Step '1. 建立匿名会话（Cookie 罐 A）'
$sessionA = (Invoke-Api -Method POST -Path '/api/v1/session' -Jar $JarA -Extra @('-H', "Origin: $Origin")) | ConvertFrom-Json
Write-Host "   身份：$($sessionA.displayName)"
Write-Host "   会话 Cookie 已写入 $JarA"

Write-Step '2. 读取分类与模板'
$categories = (Invoke-Api -Method GET -Path '/api/v1/categories' -Jar $JarA) | ConvertFrom-Json
$category = $categories.items[0]
Write-Host "   分类数：$($categories.items.Count)，首个分类：$($category.id)（$($category.name)）"

$templates = (Invoke-Api -Method GET -Path "/api/v1/templates?categoryId=$($category.id)" -Jar $JarA) | ConvertFrom-Json
$templateId = $templates.items[0].id
$detail = (Invoke-Api -Method GET -Path "/api/v1/templates/$templateId" -Jar $JarA) | ConvertFrom-Json
$required = [int]$detail.requiredImageCount
Write-Host "   模板：$($detail.title) 需要 $required 张图，接受 $($detail.acceptedMimeTypes[0])"

Write-Step "3. 上传 $required 张素材（每张单次请求，返回 201）"
$sampleFiles = @(
    (Join-Path $Samples 'pet-sample.png'),
    (Join-Path $Samples 'travel-island-a.png'),
    (Join-Path $Samples 'travel-island-b.png')
)
$assetIds = @()
for ($i = 0; $i -lt $required; $i++) {
    $file = $sampleFiles[$i]
    if (-not (Test-Path $file)) { Stop-Demo "缺少示例素材 $file" }
    $uploaded = (Invoke-Api -Method POST -Path '/api/v1/assets' -Jar $JarA -Extra @(
            '-H', "Origin: $Origin",
            '-F', "templateId=$templateId",
            '-F', "file=@$file;type=image/png"
        )) | ConvertFrom-Json
    if (-not $uploaded.id) { Stop-Demo "上传失败：$($uploaded | ConvertTo-Json -Depth 5)" }
    $assetIds += $uploaded.id
    Write-Host "   上传 $file -> assetId=$($uploaded.id)"
}
$assetJson = ($assetIds | ForEach-Object { '"' + $_ + '"' }) -join ','

Write-Step '4. 创建成功任务（无 simulation，使用后端默认耗时）'
$created = (Invoke-Json -Method POST -Path '/api/v1/tasks' -Jar $JarA -Body (
        "{`"templateId`":`"$templateId`",`"assetIds`":[$assetJson]}")) | ConvertFrom-Json
Write-Host "   202 受理：taskId=$($created.id) status=$($created.status) revision=$($created.revision)"

$terminal = Wait-Task -Jar $JarA -TaskId $created.id
Write-Host "   终态：status=$($terminal.status) currentStage=$($terminal.currentStage)"
Show-Steps $terminal
if ($terminal.status -ne 'SUCCEEDED') { Stop-Demo "期望 SUCCEEDED" }

Write-Step '5. 校验结果 URL 返回真实图片字节'
Test-ResultBytes -Jar $JarA -TaskId $created.id

Write-Step '6. 查询个人历史'
$history = (Invoke-Api -Method GET -Path '/api/v1/tasks?limit=5' -Jar $JarA) | ConvertFrom-Json
Write-Host "   本页 $($history.items.Count) 条，nextCursor=$($history.nextCursor)"
foreach ($item in $history.items) {
    Write-Host ("   {0} {1,-9}{2}" -f $item.id, $item.status, $item.templateTitle)
}

Write-Step '7. 三个阶段各创建一次失败任务'
foreach ($stage in @('ASSET_CHECK', 'GENERATE', 'FINALIZE')) {
    $body = "{`"templateId`":`"$templateId`",`"assetIds`":[$assetJson],`"simulation`":{`"failAt`":`"$stage`",`"stageDelayMs`":{`"ASSET_CHECK`":300,`"GENERATE`":300,`"FINALIZE`":300}}}"
    $made = (Invoke-Json -Method POST -Path '/api/v1/tasks' -Jar $JarA -Body $body) | ConvertFrom-Json
    $failed = Wait-Task -Jar $JarA -TaskId $made.id
    $errCode = if ($failed.error) { "$($failed.error.code)@$($failed.error.failedStage)" } else { 'null' }
    Write-Host ("   failAt={0,-12}status={1} error={2} result={3}" -f `
            $stage, $failed.status, $errCode, $(if ($null -eq $failed.result) { 'null' } else { 'present' }))
    Show-Steps $failed
    if ($failed.status -ne 'FAILED') { Stop-Demo "failAt=$stage 期望 FAILED" }
    if ($null -ne $failed.result) { Stop-Demo '失败任务不应有结果' }
}

Write-Step '8. 以每阶段 4 秒连续创建三个任务（观察排队与串行执行）'
$longIds = @()
for ($i = 1; $i -le 3; $i++) {
    $body = "{`"templateId`":`"$templateId`",`"assetIds`":[$assetJson],`"simulation`":{`"failAt`":null,`"stageDelayMs`":{`"ASSET_CHECK`":4000,`"GENERATE`":4000,`"FINALIZE`":4000}}}"
    $made = (Invoke-Json -Method POST -Path '/api/v1/tasks' -Jar $JarA -Body $body) | ConvertFrom-Json
    $longIds += $made.id
    Write-Host "   受理 task#$i=$($made.id) status=$($made.status)（约 12 秒后完成）"
}
Write-Host '   3 秒后观察队列状态（应看到一个 RUNNING，其余 QUEUED）：'
Start-Sleep -Seconds 3
foreach ($id in $longIds) {
    $snapshot = (Invoke-Api -Method GET -Path "/api/v1/tasks/$id" -Jar $JarA) | ConvertFrom-Json
    Write-Host ("   {0} status={1,-9}currentStage={2}" -f $id, $snapshot.status, $snapshot.currentStage)
}
Write-Host '   等待全部完成：'
foreach ($id in $longIds) {
    $final = Wait-Task -Jar $JarA -TaskId $id -TimeoutSeconds 120
    Write-Host "   $id -> $($final.status)"
}

Write-Step '9. 第二个 Cookie 罐验证身份隔离'
$sessionB = (Invoke-Api -Method POST -Path '/api/v1/session' -Jar $JarB -Extra @('-H', "Origin: $Origin")) | ConvertFrom-Json
Write-Host "   身份 B：$($sessionB.displayName)"
$code = & curl.exe -sS --noproxy '*' -o NUL -w '%{http_code}' -b $JarB "$BaseUrl/api/v1/tasks/$($created.id)"
Write-Host "   B 读取 A 的任务 -> HTTP $code（期望 404）"
if ($code -ne '404') { Stop-Demo "身份隔离失败：B 读到了 A 的任务（$code）" }
$historyB = (Invoke-Api -Method GET -Path '/api/v1/tasks?limit=5' -Jar $JarB) | ConvertFrom-Json
Write-Host "   B 的历史条数：$($historyB.items.Count)（期望 0）"
if ($historyB.items.Count -ne 0) { Stop-Demo 'B 的历史不为空' }

Write-Step '完成'
Write-Host '   A 的任务总数应为 1 + 3 + 3 = 7，可在 http://localhost:3000/history 查看。'
Write-Host '   手工补足的两步：刷新页面确认历史恢复；关闭页面再打开确认后台仍执行。'
Write-Host "   本次产生的文件保留在 $RunDir，脚本不做任何递归清理。"
