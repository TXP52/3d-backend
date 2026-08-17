# Đăng nhập trang quản trị & cấu hình gửi OTP qua Gmail

## Tài khoản quản trị

| | |
|---|---|
| Email | `txp5201aquarius@gmail.com` |
| Mật khẩu | `txP12345678@` |
| Vai trò | `admin` |

Tài khoản này được backend **tự tạo/tự sửa cho đúng** mỗi lần khởi động (xem `DataSeeder.java`).
Muốn tắt hành vi đó: đặt biến môi trường `IN3D_TU_TAO_ADMIN=false`.

## Luồng đăng nhập (2 bước)

1. Mở `http://localhost:8124/dang-nhap.html`, nhập **email + mật khẩu**.
   - Sai mật khẩu → *"Email hoặc mật khẩu không đúng"*.
   - Đúng nhưng **không phải admin** → bị từ chối (chỉ admin vào được trang quản trị).
2. Hệ thống gửi **mã OTP 6 số** về email → nhập mã để vào.
   - Mã sống **5 phút**, **dùng một lần**, nhập sai tối đa **5 lần**.
   - Có nút **Gửi lại mã** và đồng hồ đếm ngược.

Trang quản trị **không có chức năng đăng ký** — tài khoản do quản trị viên tạo/cấp quyền.

## Bật gửi OTP qua Gmail thật

Mặc định (chưa cấu hình) backend **không gửi email**, mã OTP được **in ra cửa sổ chạy backend**:

```
[IN3D] CHƯA CẤU HÌNH GMAIL — mã OTP chỉ hiển thị ở đây:
       Email : txp5201aquarius@gmail.com
       Mã OTP: 123456   (hiệu lực 5 phút)
```

Để gửi email thật, cần **App Password của Google** (không dùng mật khẩu Gmail thường):

1. Bật **Xác minh 2 bước** cho tài khoản Google: https://myaccount.google.com/security
2. Vào https://myaccount.google.com/apppasswords → tạo app password mới (đặt tên "IN3D Store")
3. Google trả về chuỗi 16 ký tự dạng `abcd efgh ijkl mnop`
4. Đặt biến môi trường rồi chạy backend:

**PowerShell:**
```powershell
$env:GMAIL_USER = "txp5201aquarius@gmail.com"
$env:GMAIL_APP_PASSWORD = "abcd efgh ijkl mnop"
cd D:\3d\3d-backend
java -jar target\in3d-backend-1.0.0.jar
```

**CMD:**
```bat
set GMAIL_USER=txp5201aquarius@gmail.com
set GMAIL_APP_PASSWORD=abcd efgh ijkl mnop
cd D:\3d\3d-backend
java -jar target\in3d-backend-1.0.0.jar
```

Khi cấu hình đúng, console in `[IN3D] Đã gửi mã OTP tới ...` và email nhận được có mã 6 số cỡ lớn.

> **Lưu ý bảo mật:** đừng ghi App Password vào file trong dự án (dễ lộ khi push GitHub) — chỉ đặt qua biến môi trường như trên.

## Đổi mật khẩu quản trị

Sửa hằng `ADMIN_MAT_KHAU` trong `3d-backend/src/main/java/vn/in3d/backend/config/DataSeeder.java`, build lại rồi khởi động — backend sẽ đặt lại mật khẩu cho khớp.
