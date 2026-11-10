# 启动前检查端口占用，冲突则中止并提示，避免干扰本机已有服务。
# 兼容 PowerShell 7：pwsh -File scripts/dev-up.ps1

$ErrorActionPreference = 'Stop'

$requiredPorts = @(($env:INKPRESS_PORT ? [int]$env:INKPRESS_PORT : 8912))

foreach ($p in $requiredPorts) {
    $conn = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        $pidOwner = ($conn | Select-Object -First 1).OwningProcess
        $proc = (Get-Process -Id $pidOwner -ErrorAction SilentlyContinue).ProcessName
        Write-Error "端口 $p 已被占用（进程：$proc，PID：$pidOwner）。请设置 INKPRESS_PORT 环境变量改用其他端口，或停止该进程。"
    }
}

Write-Output "端口检查通过，启动 inkpress 服务组..."
docker compose up -d --build
docker compose ps
