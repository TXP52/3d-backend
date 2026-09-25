# ============================================================
# CHẠY BACKEND trên máy server.
#
# Dùng được cả hai kiểu:
#   - Gõ tay để thử:  .\may-chu\chay.ps1
#   - Task Scheduler gọi lúc máy khởi động (xem cai-tu-chay.ps1)
#
# Mật khẩu database KHÔNG nằm trong file này. Nó nằm ở
#   <thư mục repo>\config\application-supabase.properties
# Spring Boot tự tìm thư mục config\ cạnh chỗ chạy. Thư mục đó đã bị .gitignore
# chặn nên không bao giờ lên GitHub.
# ============================================================

$ErrorActionPreference = 'Stop'

# Thư mục repo = thư mục cha của may-chu\
$goc = Split-Path -Parent $PSScriptRoot
Set-Location $goc

$jar = Join-Path $goc 'target\in3d-backend-1.0.0.jar'
$nhatKy = Join-Path $PSScriptRoot 'backend.log'

# ---------- Tìm Java ----------
# Spring Boot 3.3.4 chạy được Java 17 tới 22. Máy có sẵn Java 25 thì KHÔNG dùng
# được: thư viện Hibernate bên trong chưa đọc nổi định dạng lớp của bản đó.
# Nên ưu tiên tìm đúng bản 21 hoặc 17 đã cài, thay vì lấy bừa java trong PATH.
function TimJava {
    if ($env:IN3D_JAVA -and (Test-Path $env:IN3D_JAVA)) { return $env:IN3D_JAVA }

    $thuMucCai = @(
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Amazon Corretto"
    )
    foreach ($tm in $thuMucCai) {
        if (-not (Test-Path $tm)) { continue }
        $ban = Get-ChildItem $tm -Directory -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -match 'jdk-?(21|17)' } |
               Sort-Object Name -Descending
        foreach ($b in $ban) {
            $duong = Join-Path $b.FullName 'bin\java.exe'
            if (Test-Path $duong) { return $duong }
        }
    }
    return 'java'   # đành dùng bản trong PATH
}

$java = TimJava

if (-not (Test-Path $jar)) {
    Write-Host "Chua co file jar. Chay lenh nay truoc:" -ForegroundColor Yellow
    Write-Host "  .\mvnw.cmd -B package -DskipTests" -ForegroundColor Yellow
    exit 1
}

# ---------- Cắt bớt nhật ký khi phình to ----------
if ((Test-Path $nhatKy) -and ((Get-Item $nhatKy).Length -gt 20MB)) {
    Move-Item $nhatKy "$nhatKy.cu" -Force
}

"=== Khoi dong $(Get-Date -Format 'dd/MM/yyyy HH:mm:ss') — java: $java ===" | Out-File $nhatKy -Append -Encoding utf8

# Chạy thẳng (không Start-Process): Task Scheduler coi tiến trình này là tác vụ,
# tắt tác vụ là tắt backend, khỏi phải đi tìm số hiệu tiến trình.
& $java -jar $jar *>> $nhatKy
