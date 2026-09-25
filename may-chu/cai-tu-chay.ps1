# ============================================================
# BẢO WINDOWS TỰ CHẠY BACKEND MỖI KHI MÁY KHỞI ĐỘNG.
#
# Chạy MỘT LẦN, bằng PowerShell mở với quyền Administrator:
#   .\may-chu\cai-tu-chay.ps1
#
# Vì sao không chạy tay trong cửa sổ PowerShell: thoát Remote Desktop kiểu
# "Sign out" là Windows đóng luôn mọi thứ đang chạy trong phiên đó, backend chết
# theo. Tác vụ này chạy dưới tài khoản SYSTEM nên không dính vào phiên đăng nhập
# nào, bạn thoát Remote Desktop hay khởi động lại máy nó vẫn sống.
#
# Gỡ đi khi không cần:  Unregister-ScheduledTask -TaskName In3dBackend
# Xem nhật ký:          Get-Content .\may-chu\backend.log -Tail 50 -Wait
# ============================================================

$ErrorActionPreference = 'Stop'

$TEN_TAC_VU = 'In3dBackend'
$goc  = Split-Path -Parent $PSScriptRoot
$chay = Join-Path $PSScriptRoot 'chay.ps1'

if (-not (Test-Path $chay)) { throw "Khong thay $chay" }

# Phải là Administrator mới đăng ký được tác vụ chạy bằng SYSTEM
$toi = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $toi.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Hay mo PowerShell bang quyen Administrator roi chay lai."
}

$viecLam = New-ScheduledTaskAction `
    -Execute 'powershell.exe' `
    -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$chay`"" `
    -WorkingDirectory $goc

$khiNao = New-ScheduledTaskTrigger -AtStartup

$chayBang = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

# ExecutionTimeLimit 0 = chạy mãi, Windows không tự cắt sau 3 ngày như mặc định.
# RestartCount/Interval: backend lỡ chết thì một phút sau tự bật lại.
$caiDat = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $TEN_TAC_VU `
    -Action $viecLam -Trigger $khiNao -Principal $chayBang -Settings $caiDat `
    -Description 'Backend Java cua shop in 3D (Spring Boot)' -Force | Out-Null

Start-ScheduledTask -TaskName $TEN_TAC_VU

Write-Host "Da dang ky va bat tac vu '$TEN_TAC_VU'." -ForegroundColor Green
Write-Host "Cho khoang 30 giay roi kiem tra:" -ForegroundColor Cyan
Write-Host '  Invoke-RestMethod http://localhost:8090/api/suc-khoe'
