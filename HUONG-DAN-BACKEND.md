# Hướng dẫn Backend chung — IN3D Shop

Hệ thống gồm **2 website + backend Java, dùng chung 1 cơ sở dữ liệu**:

```
D:\3d
├── Website-GameOver\   → Website BÁN HÀNG (khách xem sản phẩm, đặt hàng, đăng ký/đăng nhập)
├── 3d\                 → Trang QUẢN TRỊ (admin xem đơn hàng, sản phẩm, người dùng)
├── 3d-backend\         → BACKEND JAVA (Spring Boot, cổng 8090) — xem 3d-backend/README.md
├── backend\
│   └── schema.sql      → Schema cơ sở dữ liệu dùng chung (chạy 1 lần trên Supabase)
└── tools\              → Maven portable (dùng để build backend Java)
```

**Cách 2 frontend chọn backend (tự động):**
1. Backend **Java** đang chạy tại `http://localhost:8090` → mọi thao tác đơn hàng/sản phẩm đi qua Java.
2. Java tắt → gọi thẳng **Supabase** (REST + RLS).
3. Mất mạng → đơn lưu tạm trên trình duyệt.

Đăng nhập/đăng ký luôn dùng **Supabase Auth** (cả 2 trang).

**Backend:** Supabase project `https://nmptxzbtngztzxpwdprs.supabase.co`

Cả hai website đều nhúng publishable key (an toàn để công khai). Phân quyền được kiểm soát bằng **Row Level Security** ngay trong database:
- Khách (kể cả chưa đăng nhập) chỉ xem được sản phẩm và tạo đơn hàng.
- Người đăng nhập xem được đơn của chính mình.
- Chỉ tài khoản có `vai_tro = 'admin'` mới sửa/xoá đơn, quản lý sản phẩm, xem toàn bộ người dùng.

---

## Bước 1: Tạo bảng dữ liệu (làm 1 lần duy nhất)

1. Mở [Supabase Dashboard](https://supabase.com/dashboard) → chọn project của bạn.
2. Vào **SQL Editor** (biểu tượng ▶ bên trái) → **New query**.
3. Mở file `backend/schema.sql`, **copy toàn bộ** nội dung, dán vào và bấm **Run**.

Schema sẽ tạo:

| Bảng | Nội dung |
|---|---|
| `profiles` | Hồ sơ người dùng (họ tên, email, SĐT, vai trò khách/admin) — tự tạo khi đăng ký |
| `danh_muc` | Danh mục sản phẩm (6 danh mục mẫu) |
| `san_pham` | Sản phẩm (28 sản phẩm mẫu đúng với web) |
| `don_hang` | Đơn đặt hàng (hỗ trợ cả khách vãng lai) |
| `don_hang_chi_tiet` | Từng món trong đơn |
| `thanh_toan` | Thanh toán (COD/chuyển khoản/ví/thẻ, trạng thái) |

## Bước 2: Tạo tài khoản admin

1. Mở website bán hàng → trang **Đăng nhập** → **Đăng ký** một tài khoản (email + mật khẩu).
   - Nếu Supabase bật xác nhận email: kiểm tra hộp thư và bấm link xác nhận.
   - (Tắt xác nhận email tại: Dashboard → Authentication → Sign In / Up → tắt "Confirm email".)
2. Quay lại **SQL Editor**, chạy câu lệnh (thay email của bạn):

```sql
update public.profiles set vai_tro = 'admin'
where id = (select id from auth.users where email = 'email-cua-ban@example.com');
```

## Bước 3: Chạy 2 website

**Website bán hàng** (`Website-GameOver`):
```bash
cd D:\3d\Website-GameOver; python -m http.server 8123
```
→ mở `http://localhost:8123`

**Trang quản trị** (`3d/public`):
```bash
cd D:\3d\3d\public; python -m http.server 8124
```
→ mở `http://localhost:8124/dang-nhap.html`

## Luồng hoạt động

1. Khách vào website bán hàng → bấm **Đặt hàng** → điền thông tin → đơn được ghi vào bảng `don_hang` + `don_hang_chi_tiet` + `thanh_toan` trên Supabase (khách không cần đăng nhập; nếu đã đăng nhập thì đơn gắn với tài khoản).
2. Admin đăng nhập trang quản trị (`dang-nhap.html`) bằng tài khoản có quyền admin.
3. Trang quản trị hiển thị **dữ liệu thật** từ cùng database:
   - **App → E Commerce → Orders → Order list**: danh sách đơn, đổi trạng thái (chờ xác nhận → đang giao → hoàn thành...), đánh dấu đã thanh toán, xoá đơn.
   - **App → E Commerce → Product → Product list**: danh sách sản phẩm, sửa giá/tồn kho, ẩn/hiện, thêm sản phẩm mới.
   - **App → E Commerce → Customers**: danh sách người dùng đã đăng ký kèm vai trò.

## Các file kết nối chính

| File | Vai trò |
|---|---|
| `backend/schema.sql` | Schema + RLS + dữ liệu mẫu (chạy trên Supabase) |
| `Website-GameOver/assets/js/supabase-client.js` | Kết nối Supabase cho web bán hàng |
| `Website-GameOver/assets/js/order.js` | Giỏ hàng + lưu đơn vào DB (offline thì lưu tạm trình duyệt) |
| `Website-GameOver/Login.html` | Đăng ký / đăng nhập khách hàng (Supabase Auth) |
| `3d/public/assets/js/supabase-admin.js` | Kết nối + hiển thị dữ liệu cho trang quản trị |
| `3d/public/dang-nhap.html` | Đăng nhập admin (kiểm tra quyền `vai_tro='admin'`) |

## Lưu ý bảo mật

- Publishable key được phép nằm trong code client — quyền hạn thật sự do RLS trong database quyết định.
- **Không bao giờ** đưa `service_role` key vào code client.
- Muốn thêm admin mới: lặp lại Bước 2 với email khác.
