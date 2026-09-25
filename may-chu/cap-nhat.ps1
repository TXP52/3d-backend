# ============================================================
# CẬP NHẬT BACKEND trên máy server sau khi có code mới trên GitHub.
#
#   .\may-chu\cap-nhat.ps1
#
# Làm đúng 4 việc: tắt backend, kéo code mới, dựng lại, bật backend.
# Mở bằng quyền Administrator (vì tác vụ chạy dưới SYSTEM).
# ============================================================

$ErrorActionPreference = 'Stop'

$TEN_TAC_VU = 'In3dBackend'
$goc = Split-Path -Parent $PSScriptRoot
Set-Location $goc

$dangChay = Get-ScheduledTask -TaskName $TEN_TAC_VU -ErrorAction SilentlyContinue

if ($dangChay) {
    Write-Host "Tat backend..." -ForegroundColor Cyan
    Stop-ScheduledTask -TaskName $TEN_TAC_VU
    # Tác vụ dừng nhưng tiến trình java có thể còn vài giây mới nhả cổng 8090
    Start-Sleep -Seconds 3
}

Write-Host "Keo code moi tu GitHub..." -ForegroundColor Cyan
git pull --ff-only

Write-Host "Dung lai ban chay..." -ForegroundColor Cyan
.\mvnw.cmd -B package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Dung ban chay that bai. Backend cu van con nguyen, chua bat lai." }

if ($dangChay) {
    Write-Host "Bat lai backend..." -ForegroundColor Cyan
    Start-ScheduledTask -TaskName $TEN_TAC_VU
    Start-Sleep -Seconds 20
    try {
        $kq = Invoke-RestMethod http://localhost:8090/api/suc-khoe -TimeoutSec 10
        Write-Host "Backend da chay lai: $($kq | ConvertTo-Json -Compress)" -ForegroundColor Green
    } catch {
        Write-Host "Chua goi duoc /api/suc-khoe. Xem nhat ky:" -ForegroundColor Yellow
        Write-Host '  Get-Content .\may-chu\backend.log -Tail 60'
    }
} else {
    Write-Host "Xong. Chua cai tu chay, chay .\may-chu\cai-tu-chay.ps1 neu muon." -ForegroundColor Yellow
}
