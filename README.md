# IN3D Backend — Java Spring Boot

Backend Java cho hệ thống bán hàng IN3D Shop, phục vụ cả **website bán hàng** (`Website-GameOver`) và **trang quản trị** (`3d`).

## Công nghệ

- Java 17 + Spring Boot 3.3 (Web, Data JPA, Validation)
- Database:
  - **Mặc định:** H2 (file `./data/in3d.mv.db`) — chạy thử ngay không cần cài gì
  - **Profile `supabase`:** PostgreSQL của Supabase (database dùng chung, schema tại `../backend/schema.sql`)

## Chạy backend

Yêu cầu: JDK 17. Maven portable có sẵn tại `D:\3d\tools\apache-maven-3.9.9`.

```bash
cd D:\3d\3d-backend
D:\3d\tools\apache-maven-3.9.9\bin\mvn.cmd package -DskipTests
java -jar target\in3d-backend-1.0.0.jar
```

Backend chạy tại **http://localhost:8090** (tránh 8080 vì Apache/XAMPP thường chiếm).

## API

| Method | Đường dẫn | Chức năng |
|---|---|---|
| POST | `/api/auth/dang-ky` | Đăng ký tài khoản (BCrypt, lưu bảng `nguoi_dung`; **người đầu tiên = admin**) |
| POST | `/api/auth/dang-nhap` | Đăng nhập, trả token HMAC hạn 7 ngày + thông tin người dùng |
| GET | `/api/auth/toi` | Thông tin người dùng của token (`Authorization: Bearer <token>`) |
| GET | `/api/nguoi-dung` | Danh sách tài khoản — chỉ token admin |
| GET | `/api/suc-khoe` | Kiểm tra backend sống (frontend dùng để tự chọn Java hay Supabase) |
| GET | `/api/san-pham` | Danh sách sản phẩm đang bán (`?tatCa=true`: cả sản phẩm ẩn, cho admin) |
| POST | `/api/san-pham` | Thêm sản phẩm (admin) |
| PUT | `/api/san-pham/{id}` | Sửa giá / tồn kho / ẩn-hiện (body: `{"gia":..,"tonKho":..,"dangBan":..}`) |
| POST | `/api/don-hang` | Khách đặt hàng (validate tiếng Việt, tự tạo mã đơn + bản ghi thanh toán COD) |
| GET | `/api/don-hang` | Danh sách đơn kèm chi tiết + thanh toán, mới nhất trước |
| PUT | `/api/don-hang/{id}/trang-thai` | Đổi trạng thái (body: `{"trangThai":"dang_giao"}`) |
| PUT | `/api/don-hang/{id}/da-thanh-toan` | Đánh dấu đã thanh toán |
| DELETE | `/api/don-hang/{id}` | Xoá đơn |

Ví dụ đặt hàng:

```bash
curl -X POST http://localhost:8090/api/don-hang -H "Content-Type: application/json" -d "{\"tenKhach\":\"Nguyễn Văn A\",\"soDienThoai\":\"0901234567\",\"diaChi\":\"123 Lê Lợi, Q.1\",\"matHang\":[{\"ten\":\"Nhựa PLA+ 1.75mm (1kg)\",\"donGia\":290000,\"soLuong\":2}]}"
```

## Cách frontend sử dụng

Cả hai frontend **tự động ưu tiên backend Java**, nếu Java tắt thì quay về gọi thẳng Supabase, mất mạng nữa thì lưu tạm trình duyệt:

- Website bán hàng: `Website-GameOver/assets/js/order.js` (hằng `JAVA_API`)
- Trang quản trị: `3d/public/assets/js/supabase-admin.js` (hằng `JAVA_API`)

Trang admin hiển thị nguồn dữ liệu đang dùng ngay trên tiêu đề bảng: "Đơn hàng (Java API)" hoặc "Đơn hàng (Supabase)".

## Nối vào PostgreSQL của Supabase (database dùng chung)

1. Chạy `../backend/schema.sql` trong Supabase SQL Editor (nếu chưa).
2. Lấy thông tin kết nối: Dashboard → nút **Connect** → mục **Session pooler**.
   **Bắt buộc dùng host pooler** (`aws-1-<region>.pooler.supabase.com`) vì hỗ trợ IPv4 —
   host trực tiếp `db.<ref>.supabase.co` chỉ có IPv6, sẽ bị lỗi `Connect timed out`
   (hiện ra dưới dạng "Unable to determine Dialect without JDBC metadata").
3. Đặt 3 biến môi trường rồi chạy:

**PowerShell:**
```powershell
$env:SUPABASE_JDBC_URL    = "jdbc:postgresql://aws-1-<region>.pooler.supabase.com:5432/postgres"
$env:SUPABASE_DB_USER     = "postgres.nmptxzbtngztzxpwdprs"
$env:SUPABASE_DB_PASSWORD = "mat-khau-database"
java -jar target\in3d-backend-1.0.0.jar --spring.profiles.active=supabase
```

**CMD:**
```bat
set SUPABASE_JDBC_URL=jdbc:postgresql://aws-1-<region>.pooler.supabase.com:5432/postgres
set SUPABASE_DB_USER=postgres.nmptxzbtngztzxpwdprs
set SUPABASE_DB_PASSWORD=mat-khau-database
java -jar target\in3d-backend-1.0.0.jar --spring.profiles.active=supabase
```

Khi khởi động, backend **tự kiểm tra kết nối** và in thông báo tiếng Việt:
- Thiếu biến môi trường → hướng dẫn đặt biến.
- Kết nối thất bại → in nguyên nhân gốc + 5 bước kiểm tra (host pooler, user có đuôi project, mật khẩu, project bị paused, tường lửa).
- Thành công → `[IN3D] ✔ Kết nối Supabase PostgreSQL thành công`.

Entity Java ánh xạ **đúng tên bảng/cột** của schema Supabase (`don_hang`, `san_pham`, `thanh_toan`...) nên không cần sửa code — chỉ đổi profile.

> Lưu ý: kết nối JDBC bằng user `postgres` bỏ qua RLS (toàn quyền), vì vậy khi triển khai thật hãy giữ backend Java trong server riêng và thêm xác thực (JWT/Spring Security) cho các endpoint admin — hiện tại API đang mở cho môi trường phát triển.

## Cấu trúc code

```
src/main/java/vn/in3d/backend/
├── In3dBackendApplication.java   # điểm khởi động
├── config/   CorsConfig, DataSeeder (nạp 10 sản phẩm mẫu khi H2 trống)
├── entity/   SanPham, DonHang, DonHangChiTiet, ThanhToan (ánh xạ schema Supabase)
├── repository/  SanPhamRepository, DonHangRepository (Spring Data JPA)
├── dto/      DatHangRequest (validate tiếng Việt)
├── service/  DonHangService (nghiệp vụ: tạo đơn, đổi trạng thái, thanh toán)
└── web/      SanPhamController, DonHangController, LoiValidateHandler
```
