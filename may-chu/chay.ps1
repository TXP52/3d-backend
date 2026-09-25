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

# PowerShell 5.1 mặc định ghi file chuyển hướng (>>) theo UTF-16, mà dòng tiêu đề
# bên dưới lại ghi UTF-8 — một file hai kiểu mã thì mở ra chữ rời rạc từng ký tự.
# Dòng này bắt mọi lần ghi file trong script đều dùng UTF-8.
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

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

# Hỏi thẳng java.exe xem nó là bản mấy. Không tin theo tên thư mục: máy có thể
# cài nhiều bản, mà bản trong PATH thường là bản mới nhất — đúng cái không dùng được.
function SoHieuJava($duong) {
    # java in phiên bản ra LUỒNG LỖI chứ không phải luồng thường. Đầu script đang để
    # $ErrorActionPreference = 'Stop' nên mỗi dòng đó bị coi là lỗi nặng và ném ra
    # ngoài — đọc bản nào cũng thành 0. Gán lại ngay trong hàm: PowerShell tự tạo
    # bản riêng cho hàm này, ra khỏi hàm là giá trị cũ trở lại.
    $ErrorActionPreference = 'Continue'
    try {
        $dong = (& $duong -version 2>&1 | Out-String)
        if ($dong -match 'version "(\d+)') { return [int]$Matches[1] }
    } catch { }
    return 0
}

$java = TimJava
$ban  = SoHieuJava $java

# Java 8 tu khai la "1.8.0_xxx" nen doc ra so 1, khong doc duoc thi la 0 —
# ca hai deu nho hon 17 nen dieu kien nay bat het.
if ($ban -lt 17 -or $ban -gt 22) {
    Write-Host ""
    Write-Host "Java dang dung KHONG chay duoc backend nay." -ForegroundColor Red
    Write-Host "  Duong dan : $java"
    Write-Host "  Ban       : $(if ($ban -eq 0) { 'khong doc duoc' } else { $ban })"
    Write-Host "  Can       : Java 17 den 22 (Spring Boot 3.3.4 chua chay duoc ban moi hon)."
    Write-Host ""
    Write-Host "Cac ban java tim thay tren may:" -ForegroundColor Cyan
    @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java",
      "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Amazon Corretto",
      "${env:ProgramFiles(x86)}\Java") |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem $_ -Directory -ErrorAction SilentlyContinue } |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Write-Host "  $_  (ban $(SoHieuJava $_))" }
    Write-Host ""
    Write-Host "Chon mot ban 17-22 o tren roi khai truoc khi chay:" -ForegroundColor Yellow
    Write-Host '  [Environment]::SetEnvironmentVariable("IN3D_JAVA", "<duong dan java.exe>", "Machine")'
    Write-Host "  (mo PowerShell moi thi bien nay moi co hieu luc)"
    exit 1
}

if (-not (Test-Path $jar)) {
    Write-Host "Chua co file jar. Chay lenh nay truoc:" -ForegroundColor Yellow
    Write-Host "  .\mvnw.cmd -B package -DskipTests" -ForegroundColor Yellow
    exit 1
}

# ---------- Cắt bớt nhật ký khi phình to ----------
if ((Test-Path $nhatKy) -and ((Get-Item $nhatKy).Length -gt 20MB)) {
    Move-Item $nhatKy "$nhatKy.cu" -Force
}

# Chuoi nay CHI dung ky tu ASCII. PowerShell 5.1 doc file .ps1 theo bang ma ANSI
# khi file khong co dau BOM, nen dau gach dai hay chu co dau nam trong chuoi se
# vo thanh ky tu nhay va lam dut cau lenh. Dung ${ban} chu khong phai $ban: vi
# dau hai cham ngay sau ten bien bi hieu la ten pham vi (kieu $env:).
"=== Khoi dong $(Get-Date -Format 'dd/MM/yyyy HH:mm:ss') - java ${ban} tai $java ===" | Out-File $nhatKy -Append -Encoding utf8

# Từ đây trở đi đừng để 'Stop' nữa: java viết cảnh báo ra luồng lỗi là chuyện
# bình thường, mà 'Stop' thì coi mỗi dòng đó là lỗi nặng và giết luôn script.
$ErrorActionPreference = 'Continue'

# Chạy thẳng (không Start-Process): Task Scheduler coi tiến trình này là tác vụ,
# tắt tác vụ là tắt backend, khỏi phải đi tìm số hiệu tiến trình.
& $java -jar $jar *>> $nhatKy
