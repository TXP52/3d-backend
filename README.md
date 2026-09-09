# IN3D Backend — Java Spring Boot

Backend Java cho hệ thống bán hàng IN3D Shop, phục vụ cả **website bán hàng** (`Website-GameOver`) và **trang quản trị** (`3d`).

## Công nghệ

- Java 17 + Spring Boot 3.3 (Web, Data JPA, Validation)
- Database: **PostgreSQL của Supabase** — database duy nhất, không còn H2.
  Thông tin kết nối để ở `src/main/resources/application-supabase.properties`
  (file này gitignore vì chứa mật khẩu; chưa có thì copy từ file `.example`).
  Schema do `SUPABASE-DONG-BO.sql` quản lý.

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
| POST | `/api/auth/dang-ky` | Đăng ký tài khoản khách (BCrypt, bảng `nguoi_dung`) |
| POST | `/api/auth/dang-nhap` | Đăng nhập KHÁCH (website bán hàng) — trả token luôn |
| POST | `/api/auth/admin/dang-nhap` | **Bước 1 admin**: đúng email+mật khẩu và là admin → gửi OTP 6 số qua email |
| POST | `/api/auth/admin/xac-thuc-otp` | **Bước 2 admin**: đúng OTP → trả token (mã dùng 1 lần, hạn 5 phút, sai tối đa 5 lần) |
| GET | `/api/auth/toi` | Thông tin người dùng của token (`Authorization: Bearer <token>`) |
| GET | `/api/nguoi-dung` | Danh sách tài khoản — chỉ token admin |
| GET/POST/PUT/DELETE | `/api/vat-tu` | Kho vật tư (máy in, cuộn nhựa): giá, số lượng, gram, đã dùng |
| PUT | `/api/vat-tu/{id}/dung-them` | Ghi nhận vừa in tốn thêm N gram (`{"gram": 5}`) |
| GET/POST/PUT/DELETE | `/api/nha-cung-cap` | Nhà cung cấp (nơi mua vật tư) |
| GET/POST/PUT/DELETE | `/api/mau-sac` | Bảng màu (tên + mã màu hex) dùng cho nhựa và sản phẩm |
| PUT | `/api/san-pham/{id}/khoi-phuc` | Khôi phục bản ghi đã xoá mềm |
| GET | `/api/san-pham/thung-rac` | Danh sách sản phẩm đã xoá mềm |
| GET | `/api/suc-khoe` | Kiểm tra backend sống (frontend dùng để tự chọn Java hay Supabase) |
| GET | `/api/san-pham` | Danh sách sản phẩm đang bán (`?tatCa=true`: cả sản phẩm ẩn, cho admin) |
| POST | `/api/san-pham` | Thêm sản phẩm (admin) |
| PUT | `/api/san-pham/{id}` | Sửa tên / mô tả / ảnh / giá / tồn kho / ẩn-hiện / trạng thái |
| DELETE | `/api/san-pham/{id}` | **Xoá mềm** sản phẩm (bật `is_deleted`, không xoá khỏi DB) |
| POST | `/api/anh` | **Tải ảnh lên** (multipart `file`) → trả `{ten, duongDan, url}`; lưu ở `./data/anh` |
| DELETE | `/api/anh/{ten}` | Xoá 1 file ảnh khỏi ổ đĩa |
| GET | `/anh/{ten}` | Xem ảnh đã tải (phục vụ tĩnh, cache 30 ngày) |
| POST | `/api/don-hang` | Khách đặt hàng (validate tiếng Việt, tự tạo mã đơn + bản ghi thanh toán COD) |
| GET | `/api/don-hang` | Danh sách đơn kèm chi tiết + thanh toán, mới nhất trước |
| PUT | `/api/don-hang/{id}/trang-thai` | Đổi trạng thái (body: `{"trangThai":"dang_giao"}`) |
| PUT | `/api/don-hang/{id}/da-thanh-toan` | Đánh dấu đã thanh toán |
| DELETE | `/api/don-hang/{id}` | **Xoá mềm** đơn hàng |

### Trạng thái

`san_pham.trang_thai` (theo quy trình in 3D):
`du_kien` · `da_dat` · `dang_in` · `san_hang` · `dang_van_chuyen` · `thanh_cong` · `hoan_hang` · `het_hang`

`vat_tu.trang_thai` (theo quy trình mua vật tư):
`da_dat` · `dang_van_chuyen` · `thanh_cong` · `het_hang`

Gửi trạng thái ngoài danh sách trên → HTTP 400 kèm thông báo tiếng Việt.

### Xoá mềm

**Toàn hệ thống không xoá cứng.** Mọi bảng kế thừa lớp `entity/BanGhi.java` nên đều có:

| Cột | Ý nghĩa |
|---|---|
| `created_at` | Lúc tạo, không đổi |
| `updated_at` | Tự cập nhật mỗi lần sửa |
| `is_deleted` | `true` = đã xoá (ẩn khỏi mọi danh sách), dữ liệu vẫn còn nguyên |

Gọi `DELETE` chỉ bật cờ `is_deleted`. Mọi API danh sách đều lọc `is_deleted = false`.
Nhờ vậy lỡ tay xoá vẫn khôi phục được, và đơn hàng cũ vẫn tra ngược được sản phẩm đã ngừng bán.

### Đồng bộ Supabase

Schema do file `SUPABASE-DONG-BO.sql` quản lý — mở Supabase → SQL Editor →
dán cả file → Run. Chạy lại bao nhiêu lần cũng được.

`ddl-auto=none`: Java **không** tự sửa schema. Đã thử để `update` và nó hỏng —
Hibernate so kiểu cột rất thô, nó đòi sửa 40 cột của Supabase (`text` thành
`varchar(255)` làm cụt mô tả sản phẩm, `numeric` thành `bigint`), riêng
`don_hang_chi_tiet.don_gia` có cột `thanh_tien` GENERATED tính từ nó nên
Postgres từ chối, Hikari đánh dấu kết nối hỏng, cả app chết lúc khởi động.

Bù lại, lúc khởi động app tự soát 10 bảng + các cột bắt buộc. Thiếu gì thì
dừng ngay kèm danh sách cụ thể và nhắc chạy `SUPABASE-DONG-BO.sql`, thay vì
để trang quản trị lỗi 500 rồi mới đi mò.

### Lưu ảnh

Ảnh ghi thẳng xuống ổ đĩa (`in3d.thu-muc-anh`, mặc định `./data/anh`), tên file là UUID, tối đa 5MB,
chỉ nhận `jpg/jpeg/png/webp/gif`. Trình duyệt đã nén ảnh còn ≤1200px WebP trước khi gửi.

- **DB lưu `duongDan` tương đối** (`/anh/xxx.webp`) — đổi host/tên miền không chết link ảnh cũ.
- Kiểm tra **magic bytes** đầu file: đổi đuôi file rác thành `.jpg` sẽ bị từ chối HTTP 415.
- Quá 5MB → HTTP 413 kèm JSON tiếng Việt (`MaxUploadSizeExceededException` được bắt riêng).
- `/anh/**` trả `X-Content-Type-Options: nosniff` và cache 1 năm `immutable`.
- ⚠️ Endpoint tải ảnh **chưa có xác thực** (giống các API admin khác) — phải bổ sung trước khi mở ra Internet.

So sánh các cách lưu ảnh miễn phí và cách chuyển sang Supabase Storage khi deploy: xem [HUONG-DAN-LUU-ANH.md](HUONG-DAN-LUU-ANH.md).

```bash
curl -X POST http://localhost:8090/api/anh -F "file=@anh-san-pham.jpg"
```

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
3. Chạy như bình thường — `application.properties` đã bật sẵn profile `supabase`:

```powershell
java -jar target\in3d-backend-1.0.0.jar
```

Khi khởi động, backend **tự kiểm tra kết nối** và in thông báo tiếng Việt:
- Thiếu biến môi trường → hướng dẫn đặt biến.
- Kết nối thất bại → in nguyên nhân gốc + 5 bước kiểm tra (host pooler, user có đuôi project, mật khẩu, project bị paused, tường lửa).
- Thành công → `[IN3D] ✔ Kết nối Supabase PostgreSQL thành công`.

- Schema thiếu bảng/cột → in danh sách thiếu + nhắc chạy `SUPABASE-DONG-BO.sql`.

Entity Java ánh xạ **đúng tên bảng/cột** của schema Supabase
(`don_hang`, `san_pham`, `thanh_toan`...).

> Lưu ý: kết nối JDBC bằng user `postgres` bỏ qua RLS (toàn quyền), vì vậy khi triển khai thật hãy giữ backend Java trong server riêng và thêm xác thực (JWT/Spring Security) cho các endpoint admin — hiện tại API đang mở cho môi trường phát triển.

## Cấu trúc code

```
src/main/java/vn/in3d/backend/
├── In3dBackendApplication.java   # điểm khởi động
├── config/   CorsConfig, TaiNguyenAnhConfig (phục vụ /anh/**), DataSeeder, KiemTraKetNoiSupabase
├── entity/   SanPham, DonHang, DonHangChiTiet, ThanhToan, VatTu, NhaCungCap, NguoiDung, MaOtp
├── repository/  Spring Data JPA cho từng entity
├── dto/      DatHangRequest (validate tiếng Việt)
├── service/  DonHangService, XacThucService (OTP), EmailService (Gmail SMTP)
└── web/      SanPhamController, DonHangController, VatTuController, XacThucController,
              AnhController (tải ảnh), LoiValidateHandler
```
