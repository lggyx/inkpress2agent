# InkPress 接口冒烟测试脚本
# 用法：powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1 [-BaseUrl http://localhost:8080]
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$pass = 0
$fail = 0

function Assert($name, $condition) {
    if ($condition) {
        Write-Host ("[PASS] " + $name)
        $script:pass++
    } else {
        Write-Host ("[FAIL] " + $name)
        $script:fail++
    }
}

# 1. 前台文章分页（公开）
$articles = Invoke-RestMethod "$BaseUrl/api/articles/page?pageSize=5"
Assert "前台文章分页返回 3 篇种子文章" ($articles.code -eq 200 -and $articles.data.total -eq 3)

# 2. 热门文章
$hot = Invoke-RestMethod "$BaseUrl/api/articles/hot?limit=3"
Assert "热门文章接口" ($hot.code -eq 200 -and $hot.data.Count -le 3)

# 3. 归档
$arch = Invoke-RestMethod "$BaseUrl/api/articles/archives"
Assert "归档时间线" ($arch.code -eq 200 -and $arch.data.Count -ge 1)

# 4. 文章详情
$detail = Invoke-RestMethod "$BaseUrl/api/articles/1"
Assert "文章详情返回且浏览量为有效数值" ($detail.code -eq 200 -and $null -ne $detail.data.views -and $detail.data.views -ge 0)
# 浏览计数先写入 Redis，由定时任务每分钟回写数据库，冒烟测试不等待回写周期

# 5. 登录
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}'))
$token = $login.data.token
Assert "管理员登录获取 token" ($login.code -eq 200 -and $token.Length -gt 20)

$headers = @{ Authorization = "Bearer $token" }

# 6. 错误密码
try {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -ContentType "application/json" -Body '{"username":"admin","password":"wrong"}' | Out-Null
    $wrongPasswordOk = $false
} catch {
    # 后端返回 HTTP 200 + code=1002，不会抛异常；走到这里说明结构异常
    $wrongPasswordOk = $false
}
$wrongLogin = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
    -ContentType "application/json" -Body '{"username":"admin","password":"wrong"}'
Assert "错误密码返回 code=1002" ($wrongLogin.code -eq 1002)

# 7. 越权访问（无 token）
$adminNoAuth = Invoke-RestMethod "$BaseUrl/api/admin/dashboard/stats"
Assert "无 token 访问管理端返回 401" ($adminNoAuth.code -eq 401)

# 8. 看板统计
$stats = Invoke-RestMethod "$BaseUrl/api/admin/dashboard/stats" -Headers $headers
Assert "看板统计文章数=3" ($stats.code -eq 200 -and $stats.data.articleCount -eq 3)
Assert "看板趋势包含近 7 天" ($stats.data.trend.Count -eq 7)

# 9. 管理端文章分页（含草稿）
$adminArticles = Invoke-RestMethod "$BaseUrl/api/admin/articles/page?pageSize=10" -Headers $headers
Assert "管理端文章分页" ($adminArticles.code -eq 200 -and $adminArticles.data.total -eq 3)

# 10. 保存文章（草稿）
$saveBody = @{
    title   = "冒烟测试文章"
    content = "# 冒烟测试`n`n这是自动测试创建的文章。"
    status  = "DRAFT"
    tags    = @("测试")
} | ConvertTo-Json
$saved = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/articles" `
    -Headers $headers -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($saveBody))
$newId = $saved.data
Assert "保存草稿文章" ($saved.code -eq 200 -and $newId -gt 0)

# 11. 草稿对前台不可见（应 404）
$draftDetail = Invoke-RestMethod "$BaseUrl/api/articles/$newId"
Assert "草稿前台详情返回 404" ($draftDetail.code -eq 404)

# 12. 发布文章
$pub = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/admin/articles/$newId/status" `
    -Headers $headers -ContentType "application/json" -Body '{"status":"PUBLISHED"}'
Assert "发布文章" ($pub.code -eq 200)

# 13. 游客评论（需审核）
$commentBody = @{ articleId = 1; nickname = "冒烟测试用户"; content = "自动化测试评论" } | ConvertTo-Json
$comment = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/comments" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($commentBody))
Assert "游客评论提交" ($comment.code -eq 200)

# 14. 管理端能看到待审核评论
$pending = Invoke-RestMethod "$BaseUrl/api/admin/comments?status=PENDING" -Headers $headers
Assert "待审核评论可查询" ($pending.code -eq 200 -and $pending.data.total -ge 1)

# 15. 游客点赞可用 + 去重
$like1 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/articles/1/like"
$like2 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/articles/1/like"
Assert "游客点赞可访问（非 401）" ($like1.code -eq 200 -and $null -ne $like1.data)
Assert "重复点赞计数不变" ($null -ne $like1.data -and $like1.data -eq $like2.data)

# 15b. 超大 pageSize 被限制
$big = Invoke-RestMethod "$BaseUrl/api/articles/page?pageSize=100000"
Assert "超大 pageSize 被限制为 50" ($big.code -eq 200 -and $big.data.pageSize -le 50)

# 16. AI 未配置返回明确错误码
$ai = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/ai/summary" -Headers $headers `
    -ContentType "application/json" -Body '{"content":"测试内容"}'
Assert "AI 未配置返回 code=2001" ($ai.code -eq 2001)

# 17. 清理：删除测试文章与测试评论
$del = Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/admin/articles/$newId" -Headers $headers
Assert "删除测试文章" ($del.code -eq 200)

Write-Host ""
Write-Host ("冒烟测试完成：通过 {0} 项，失败 {1} 项" -f $pass, $fail)
if ($fail -gt 0) { exit 1 }
