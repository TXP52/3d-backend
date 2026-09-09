# Hướng dẫn lưu ảnh sản phẩm — IN3D Store

> Rà soát ngày **17/08/2026**. Mọi con số hạn mức trong tài liệu này đã được đối chiếu trực tiếp với trang giá / tài liệu chính thức của từng nhà cung cấp tại ngày đó. Chỗ nào chưa kiểm chứng được đều ghi rõ `[CHƯA XÁC MINH]` — đừng coi là chắc chắn.
>
> Bối cảnh: backend Spring Boot 3.3 chạy local cổng 8090 (`D:\3d\3d-backend`), database Supabase PostgreSQL, frontend HTML/JS thuần (`D:\3d\3d\public`), đã có sẵn project Supabase `nmptxzbtngztzxpwdprs`. Ràng buộc: **không tốn tiền, không cần thẻ tín dụng, ưu tiên đơn giản**.

---

## 0. Hiện đã làm tới đâu (cập nhật 17/08/2026)

Phương án đang chạy: **lưu ảnh trên ổ đĩa máy chạy backend — 0 đồng, không quota, không cần đăng ký gì.**

| Việc | Trạng thái |
|---|---|
| `POST /api/anh` nhận ảnh, lưu `3d-backend/data/anh`, phục vụ tại `/anh/<uuid>` | ✅ xong |
| Nén ở trình duyệt: tối đa **1200px**, **WebP 82%** (lùi JPEG 80% nếu trình duyệt cũ) | ✅ xong — ảnh 3,8MB → **104KB** |
| **Lưu đường dẫn tương đối** `/anh/xxx.webp` vào DB (đổi host không chết link) | ✅ xong — hiển thị qua `Apex.anhDayDu()` |
| Chặn file giả mạo: kiểm tra magic bytes JPEG/PNG/GIF/WebP | ✅ xong — đổi đuôi file trả HTTP 415 |
| Ảnh > 5MB trả JSON tiếng Việt (HTTP 413) thay vì trang lỗi HTML | ✅ xong |
| Header `X-Content-Type-Options: nosniff` + cache 1 năm `immutable` | ✅ xong |
| Trang bán hàng (`chi-tiet.html`) hiện ảnh thật nếu có, không có thì dùng ảnh minh hoạ cũ | ✅ xong |
| Xác thực cho `POST /api/anh` | ⏳ **chưa** — cả API admin hiện đang mở, phải làm trước khi deploy |
| Sinh thêm bản thumbnail 400px | ⏳ chưa cần (lưu ổ đĩa không tốn băng thông); **bắt buộc** khi chuyển Supabase |
| Chuyển sang Supabase Storage | ⏳ chưa — làm khi deploy lên Internet, xem mục 3 |

**Tóm lại: bây giờ dùng được ngay, miễn phí.** Chỉ khi nào đưa shop lên Internet mới cần đọc tiếp mục 2.2 và mục 3.

---

## 1. Bảng so sánh phương án

| Phương án | Miễn phí đến mức nào | Cần thẻ? | Độ khó | Rủi ro |
|---|---|---|---|---|
| **Ổ đĩa local + Spring Boot phục vụ `/anh/**`** *(đang chạy)* | Không giới hạn — chỉ giới hạn bởi ổ cứng. Không quota, không băng thông | **Không** | ⭐ Rất dễ (đã có sẵn `AnhController.java`) | Máy tắt là shop offline; khách ngoài Internet không xem được `localhost:8090`; không CDN; không backup tự động; endpoint upload hiện **chưa có xác thực** |
| **Supabase Storage — bucket public `anh-san-pham`** ⭐ | 1 GB storage · 5 GB egress + 5 GB cached egress (**hai quota riêng, không cộng thành 10 GB**) · file ≤ 50 MB · 2 active project · pause sau 1 tuần không hoạt động DB | **Không** | ⭐⭐ Dễ (tạo bucket + 1 policy + `fetch()`) | Egress 5 GB là trần thật, dùng chung với DB/Auth/Realtime; project bị pause nếu DB không có hoạt động 1 tuần; vượt quota → grace period **chỉ một lần** rồi API trả 402, DB read-only; **không** có resize tự động trên gói Free |
| **Cloudinary** | 25 credits/tháng (1 credit = 1 GB storage **hoặc** 1 GB bandwidth **hoặc** 1.000 transformations — dùng chung pool, cửa sổ trượt 30 ngày) | **Không** (trang giá ghi rõ "No credit card needed") | ⭐⭐ Dễ (unsigned upload preset, POST thẳng từ browser) | Vượt quota kéo dài → **tài khoản có thể bị vô hiệu hóa** theo tài liệu hỗ trợ chính thức; unsigned preset lộ trong JS → người lạ upload rác; bậc trả phí kế tiếp nhảy thẳng 89–99 USD/tháng |
| **ImageKit.io** | 20 GB bandwidth/tháng · 3 GB storage · 2 users (reset ngày 1 hàng tháng) | `[CHƯA XÁC MINH]` — không tìm được câu khẳng định chính thức | ⭐⭐⭐ Trung bình (**bắt buộc** thêm endpoint ký HMAC trong Spring Boot) | Hết bandwidth giữa tháng → **ngừng phục vụ tính năng**, ảnh biến mất khỏi trang bán hàng; phải có backend chạy mới upload được |
| **Cloudflare R2** | 10 GB-month storage · **egress miễn phí** · 1M Class A + 10M Class B ops | **Rất nhiều khả năng CÓ** (billing policy + báo cáo người dùng; docs R2 không nói thẳng) | ⭐⭐⭐⭐ Khó (ký SigV4 / presigned URL / Worker) | Vi phạm ràng buộc "không thẻ"; pay-as-you-go — vượt là trừ tiền thật; thanh toán lỗi → mất truy cập bucket, dữ liệu giữ 30 ngày |
| **Backblaze B2 + Cloudflare** | 10 GB storage vĩnh viễn · egress free 3× dung lượng lưu, hoặc **free không giới hạn** qua CDN đối tác | **Gần như có** — bucket **public** đòi xác minh email + lịch sử thanh toán hoặc trả một khoản nhỏ qua form thẻ | ⭐⭐⭐⭐ Khó (presigned URL + cần domain riêng cho CDN) | Vi phạm ràng buộc "không thẻ"; không có resize tự động; cần domain riêng |
| **GitHub repo + jsDelivr CDN** | jsDelivr: **không giới hạn bandwidth/request** (ToS xác nhận, cho cả mục đích thương mại). GitHub: file khuyến nghị ≤ 1 MB, chặn cứng 100 MB; repo ≤ 10 GB | **Không** | ⭐⭐⭐ Trung bình (commit + tag mỗi lần thêm ảnh, hoặc gọi GitHub Contents API bằng PAT) | **ToS jsDelivr cấm đích danh** "using jsDelivr CDN as a general-purpose file or media hosting service"; GitHub AUP cho phép throttle/suspend; git giữ vĩnh viễn mọi phiên bản → repo phình; xoá ảnh phải rewrite lịch sử |
| **raw.githubusercontent.com** | Không công bố hạn mức nào | Không | ⭐ Rất dễ | GitHub đã siết rate limit cho request ẩn danh với raw.githubusercontent **nhưng không công bố con số** → không thể tính trước lúc nào ảnh vỡ hàng loạt (429); trả `Content-Type: text/plain` |
| **Uploadcare** | 1.000 operations/tháng · 5 GB traffic · 1 GB storage | Không | ⭐⭐ Dễ (widget HTML thuần) | Trang giá ghi thẳng **"Personal use only"** trên gói Free → shop bán hàng là vi phạm; "operation" định nghĩa rất rộng (mỗi upload, mỗi transformation = 1); gói trả phí rẻ nhất **66 USD/tháng** |
| **imgbb** | Ảnh ≤ 32 MB, không công bố quota | Không | ⭐ Rất dễ | **ToS cấm dùng thương mại** ("any revenue-generating endeavor or commercial enterprise") và cho phép xoá tài khoản + nội dung **bất kỳ lúc nào, không báo trước** |
| **Catbox.moe** | File ≤ 200 MB, không công bố quota | Không | ⭐⭐ (không có CORS → phải proxy qua backend) | FAQ cấm nguyên văn "image host for your business/ecommerce site"; dịch vụ sống bằng quyên góp, không SLA |
| **Base64 / bytea trong DB** | Theo quota DB: Supabase Free **500 MB/project** | Không | ⭐⭐ Dễ về code | base64 phình +33%; 300 ảnh × 800 KB → ~320 MB = 64% quota DB; vượt 500 MB → **Free project chuyển read-only, mất luôn khả năng ghi đơn hàng**; ảnh đi qua JVM+JDBC, không cache riêng từng ảnh, ăn quota **uncached** eo hẹp hơn |
| **Cloudflare Images** | 5.000 unique transformations/tháng — **chỉ cho ảnh lưu ở nơi khác**, không có storage miễn phí | Có (để dùng phần storage) | ⭐⭐⭐ | **Không phải chỗ lưu ảnh** — chỉ là lớp resize/CDN; cần domain đã trỏ nameserver về Cloudflare `[CHƯA XÁC MINH LẠI]` |
| **Render (host)** | 750 giờ instance/tháng · **5 GB băng thông** (giảm từ 100 GB ngày 23/04/2026) · ngủ sau 15 phút | Không bắt buộc để bắt đầu | ⭐⭐⭐ | Filesystem **ephemeral**: "uploaded images … are lost every time the service redeploys, restarts, or spins down" — và free service ngủ sau 15 phút vắng khách. Free **không gắn được** Persistent Disk |
| **Railway (host)** | Gói Free $0 kèm **$1 credit/tháng** · 0.5 GB RAM · **volume 0,5 GB** | `[CHƯA XÁC MINH]` | ⭐⭐⭐ | $1/tháng không đủ nuôi service chạy 24/7 → shop không online liên tục nếu không nạp tiền |
| **Fly.io (host)** | Trial **2 giờ VM runtime hoặc 7 ngày** — hết free tier định kỳ | Không cần để thử, **bắt buộc để đi tiếp** | ⭐⭐⭐⭐ | Hết trial là app dừng chạy; volume tính tiền cả khi máy đã tắt |
| **Koyeb (host)** | **Không còn gói free** — trang giá thấp nhất là Pro **$29/tháng** | Có (pre-auth $29) | — | Loại dứt khoát. Bị Mistral AI mua lại 17/02/2026, gỡ gói Starter với khách mới |

---

## 2. Khuyến nghị

### 2.1. NGAY BÂY GIỜ (backend chạy local, chỉ mình bạn dùng)

**Giữ nguyên phương án ổ đĩa local** — `AnhController.java` + `TaiNguyenAnhConfig.java` + `in3d.thu-muc-anh=./data/anh` đã chạy được rồi. Đây là phương án duy nhất **không phụ thuộc chính sách giá của bên thứ ba** (Render đã cắt băng thông 20 lần, Koyeb đã đóng free tier, Uploadcare đã siết quota — tất cả chỉ trong 2 năm qua).

Nhưng phải vá **5 điểm** ngay, vì 4 trong 5 điểm này bạn sẽ phải làm dù chọn phương án nào về sau:

1. **Lưu đường dẫn TƯƠNG ĐỐI, không lưu URL tuyệt đối.** Hiện code dùng `ServletUriComponentsBuilder.fromCurrentContextPath()` nên DB đang chứa `http://localhost:8090/anh/xxx.webp`. Đổi sang lưu `/anh/xxx.webp` và ghép tiền tố ở frontend bằng **một hằng số duy nhất**. Không sửa điểm này thì hôm deploy lên Internet **toàn bộ ảnh cũ chết link**, và việc chuyển sang Supabase sau này sẽ đau gấp mười lần.
2. **Thêm xác thực cho `POST /api/anh`.** Hiện endpoint upload không kiểm tra gì. Local thì không sao, mở ra Internet là thành chỗ cho người lạ đổ file.
3. **Nén ảnh ở phía trình duyệt ngay từ bây giờ** (mục 4). Nén sớm nghĩa là kho ảnh của bạn đã sẵn sàng để đẩy lên bất kỳ dịch vụ nào mà không phải làm lại.
4. **Re-encode ảnh bằng `ImageIO.read()` / `ImageIO.write()`** sau khi nhận file — `null` thì loại. Whitelist đuôi file là chưa đủ; re-encode xoá luôn payload giấu trong EXIF. Cấm `.svg`, `.html`, `.htm`. Thêm header `X-Content-Type-Options: nosniff` cho `/anh/**`.
5. **Thêm `@ExceptionHandler(MaxUploadSizeExceededException.class)`** vào `LoiValidateHandler.java` trả JSON 413. Hiện `AnhController` tự check `file.getSize() > 5MB` nhưng Tomcat chặn **trước khi** vào controller nên nhánh đó gần như không bao giờ chạy — admin sẽ thấy lỗi HTML xấu xí thay vì thông báo tiếng Việt.

Đồng thời đổi `setCachePeriod` sang `CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable()` — tên file là UUID nên cache vĩnh viễn là an toàn.

### 2.2. KHI DEPLOY LÊN INTERNET

**Chuyển sang Supabase Storage, bucket public `anh-san-pham`.** Lý do:

- Bạn **đã có** project Supabase và publishable key nằm sẵn trong `D:\3d\3d\public\assets\js\apex.js` (dòng 14–15). Không phải đăng ký thêm dịch vụ nào, không thêm secret nào. Chi phí tích hợp gần bằng 0 — bạn chỉ đang thiếu đúng **bước tạo bucket**.
- **Không cần thẻ tín dụng** (đã xác minh bằng văn bản chính thức — khác hẳn R2 và B2).
- 1 GB storage: 400 ảnh × 250 KB ≈ 100 MB ≈ **10% quota**. Rất thoải mái.
- Ảnh tách hẳn khỏi DB nên quota 500 MB database (tính theo từng project) dành trọn cho dữ liệu nghiệp vụ.
- Frontend HTML/JS thuần gọi được bằng `fetch()` — không npm, không build tool. Spring Boot chỉ lưu một chuỗi path trong cột ảnh.
- Hậu quả khi vượt quota **nhẹ hơn Cloudinary**: bị chặn/chậm, không có chuyện khoá tài khoản.

**Quan trọng: bất kỳ host miễn phí nào cũng làm phương án ổ đĩa trở nên vô nghĩa.** Render nói thẳng trong tài liệu rằng ảnh upload mất mỗi lần service redeploy, restart hoặc spin-down — mà free service ngủ sau 15 phút vắng khách. Nghĩa là ảnh bay **mỗi ngày**, không phải mỗi lần deploy. Đây là lý do kỹ thuật khiến việc chuyển sang Supabase Storage khi deploy không phải lựa chọn mà là **bắt buộc**.

### 2.3. Kiến trúc lai — nên làm ngay từ đầu

```
Ổ đĩa local D:\3d\3d-backend\data\anh   →  giữ ẢNH GỐC chất lượng cao (không bao giờ mất)
                 ↓ (nén ở client)
Supabase Storage  anh-san-pham/san-pham/  →  bản 1200px  (~150–250 KB)  cho trang chi tiết
                  anh-san-pham/thumb/     →  bản  400px  (~30–50  KB)  cho trang danh sách
                 ↓
DB chỉ lưu chuỗi path:  "san-pham/1755400000-ke-dien-thoai.webp"
```

Ổ đĩa local là **backup**, Supabase là **nơi phục vụ khách**. Muốn đổi nhà cung cấp sau này chỉ cần đổi một hằng số tiền tố URL.

### 2.4. Ba việc bắt buộc kèm theo khi dùng Supabase

| Việc | Vì sao |
|---|---|
| (a) Nén WebP ở client + sinh thumbnail riêng ~40 KB | **Trần thật là egress, không phải 1 GB dung lượng.** Xem tính toán ở mục 5.1 |
| (b) Cron ping DB mỗi ngày | Docs xác nhận: "a few user requests to the database each day over the previous week is enough to keep the project from being paused" |
| (c) Tạo bucket bằng Dashboard/SQL Editor | **Đừng** mở policy `INSERT` trên bảng `storage.buckets` cho role `anon` |

---

## 3. Bật Supabase Storage — từng bước

### Bước 1 — Tạo bucket public `anh-san-pham`

**Cách A (Dashboard, ~30 giây):**
`Storage` → `New bucket` → Name: `anh-san-pham` → bật **Public bucket** → `Additional configuration`:
- File size limit: `2 MB`
- Allowed MIME types: `image/jpeg, image/png, image/webp`

→ `Save`.

**Cách B (SQL Editor):**

```sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('anh-san-pham', 'anh-san-pham', true, 2097152,
        array['image/jpeg','image/png','image/webp']);
```

> **Lưu ý về quyền:** tài liệu `createBucket` ghi rõ tạo bucket cần *"buckets table permissions: insert"*. Mặc định không có policy nào cấp `INSERT` trên `storage.buckets` cho role `anon`, nên gọi từ browser bằng publishable key sẽ thất bại. Cách đúng là **dùng Dashboard hoặc SQL Editor**, không phải mở policy cho `anon` — mở ra thì ai cũng tạo bucket được trong project của bạn.

Nhân tiện: vào `Storage → Settings` xem tận mắt giá trị **global file size limit** đang là bao nhiêu. Tài liệu chỉ khẳng định *mức trần theo gói* (Free 50 MB) và câu *"you can specify the maximum file size on a per bucket level but it can't be higher than this global limit"* — **không nêu giá trị mặc định** `[CHƯA XÁC MINH]`. Nếu global limit thấp hơn 2 MB thì giới hạn bucket của bạn vô hiệu.

### Bước 2 — RLS policy

**Đọc:** bucket public thì **không cần policy nào** — docs xác nhận *"This is not needed for public buckets, as they are already publicly accessible"*.

**Ghi:** upload/update/delete **vẫn bị RLS chặn kể cả với bucket public**. Trang admin dùng publishable key và không có phiên Supabase Auth (đăng nhập admin do backend Java + OTP tự làm), mà publishable key khi chưa đăng nhập ánh xạ sang role `anon` → cần policy:

```sql
-- Cho phép upload vào đúng bucket này
create policy "anon_upload_anh" on storage.objects
  for insert to anon
  with check (bucket_id = 'anh-san-pham');

-- CHỈ thêm nếu cần ghi đè (dùng header x-upsert: true)
create policy "anon_update_anh" on storage.objects
  for update to anon
  using (bucket_id = 'anh-san-pham')
  with check (bucket_id = 'anh-san-pham');

-- CỐ Ý KHÔNG tạo policy DELETE cho anon.
```

⚠️ **Hiểu rõ đánh đổi:** publishable key nằm công khai trong `apex.js` (dòng 15). Mở `INSERT` cho `anon` nghĩa là **ai đọc được file JS đó cũng upload được vào bucket của bạn**. Ba lớp phòng thủ: giới hạn MIME + dung lượng ở bucket (bước 1), không mở DELETE, và theo dõi dung lượng định kỳ.

**Bản kín kẽ hơn (khuyên dùng khi đã deploy thật):** thêm endpoint `POST /api/anh` trong Spring Boot, backend giữ `sb_secret_...` trong **biến môi trường** rồi forward file lên cùng REST endpoint. Secret key chạy role `service_role` có thuộc tính `BYPASSRLS` nên **không cần policy anon nào** — xoá luôn hai policy ở trên. Cách này dùng lại đúng chỗ `AnhController` đang ghi ra đĩa, chỉ đổi phần "ghi file" thành "gọi REST".

### Bước 3 — Upload từ JS thuần bằng `fetch()`

Không cần npm, không cần `supabase-js`. Đúng dạng theo tài liệu *standard uploads*: `POST /storage/v1/object/{bucket}/{path}`, body là **dữ liệu file thô** (`File`/`Blob` truyền thẳng), **không phải `FormData`**.

```js
// Dùng lại SB_URL / SB_KEY đã có sẵn ở apex.js dòng 14-15
var SB_URL = 'https://nmptxzbtngztzxpwdprs.supabase.co';
var SB_KEY = 'sb_publishable_OJjvcAtUPib9bvdNNA-Bjg_vbz7CuQ-';
var BUCKET = 'anh-san-pham';

async function taiAnhLen(blob, duongDan) {
  var r = await fetch(SB_URL + '/storage/v1/object/' + BUCKET + '/' + duongDan, {
    method: 'POST',
    headers: {
      apikey: SB_KEY,
      Authorization: 'Bearer ' + SB_KEY,
      'Content-Type': blob.type,          // 'image/webp'
      'cache-control': 'max-age=31536000', // tên file bất biến -> cache 1 năm
      'x-upsert': 'true'                   // bỏ dòng này nếu không tạo policy UPDATE
    },
    body: blob                             // gửi Blob TRỰC TIẾP
  });
  if (!r.ok) throw new Error('Upload lỗi ' + r.status + ': ' + (await r.text()));
  return duongDan;                         // lưu CHUỖI NÀY vào DB
}
```

> Ảnh nén ~200 KB của dự án nằm rất sâu dưới ngưỡng khuyến nghị **6 MB** của standard upload (*"ideal for small files that are not larger than 6MB"*), nên không cần đụng tới TUS resumable upload.

### Bước 4 — URL public

```
https://nmptxzbtngztzxpwdprs.supabase.co/storage/v1/object/public/anh-san-pham/san-pham/xxx.webp
                                                                    ^bucket    ^path lưu trong DB
```

Dán thẳng vào `<img src>`. Trong code nên ghép từ một hàm duy nhất:

```js
function urlAnh(path) {
  if (!path) return 'assets/img/khong-co-anh.svg';
  if (/^https?:\/\//.test(path)) return path;            // dữ liệu cũ lỡ lưu URL tuyệt đối
  if (path.charAt(0) === '/') return JAVA_API_HOST + path; // ảnh cũ trên ổ đĩa: /anh/xxx.webp
  return SB_URL + '/storage/v1/object/public/' + BUCKET + '/' + path;
}
```

Hàm này cho phép **chuyển dần** từ ổ đĩa sang Supabase mà không phải migrate DB một lần — ảnh cũ và ảnh mới sống chung được.

### Bước 5 — Chống pause

Đặt lịch gọi 1 truy vấn REST nhẹ mỗi ngày (GitHub Actions cron, hoặc UptimeRobot gọi một endpoint `select` giới hạn 1 dòng). Đây là **hoạt động database**, không phải hoạt động storage.

> ⚠️ `[MỘT PHẦN CHƯA XÁC MINH]` Tài liệu chỉ nói *"A Free plan project is considered inactive if it does not receive sufficient user database activity over the past week"* — **chỉ nhắc tới hoạt động DATABASE**. Tài liệu **không** khẳng định traffic Storage có được tính hay không, và **không** nêu rõ URL ảnh public còn sống hay chết khi project bị pause. Phải coi cả hai là rủi ro thật và phòng bằng cron ping DB. Khôi phục: Dashboard → `Resume project`, dữ liệu và cấu hình còn nguyên, khôi phục được trong vòng 1 năm kể từ lúc pause.

---

## 4. Mẹo tiết kiệm dung lượng

### 4.1. Nén ở trình duyệt bằng canvas — BẮT BUỘC

Gói Free **không có** Image Transformation (docs xác nhận *"Image Resizing is currently enabled for Pro Plan and above"*). Không tự nén thì không có ai nén hộ.

| Bản | Cạnh dài tối đa | Định dạng | Chất lượng | Dung lượng mục tiêu | Dùng ở đâu |
|---|---|---|---|---|---|
| **Thumbnail** | **400 px** | WebP | **0.75** | 30–50 KB | Trang danh sách, giỏ hàng, bảng admin |
| **Ảnh chính** | **1200 px** | WebP | **0.82** | 120–250 KB | Trang chi tiết sản phẩm |
| Ảnh gốc | nguyên bản | JPEG/PNG | — | vài MB | **Chỉ lưu ở ổ đĩa local**, không upload |

Nếu buộc phải dùng JPEG (fallback cho trình duyệt cũ): chất lượng **0.80**, cùng kích thước. WebP ở cùng chất lượng cảm quan thường nhỏ hơn JPEG khoảng 25–35%.

```js
// Trả về Blob đã nén. canhToiDa: 400 (thumb) hoặc 1200 (ảnh chính)
function nenAnh(file, canhToiDa, chatLuong) {
  return new Promise(function (resolve, reject) {
    var img = new Image();
    img.onload = function () {
      var w = img.naturalWidth, h = img.naturalHeight;
      var ti = Math.min(1, canhToiDa / Math.max(w, h));   // KHÔNG phóng to ảnh nhỏ
      var c = document.createElement('canvas');
      c.width  = Math.round(w * ti);
      c.height = Math.round(h * ti);
      var ctx = c.getContext('2d');
      ctx.imageSmoothingQuality = 'high';
      ctx.fillStyle = '#fff';                              // nền trắng cho PNG trong suốt
      ctx.fillRect(0, 0, c.width, c.height);
      ctx.drawImage(img, 0, 0, c.width, c.height);
      URL.revokeObjectURL(img.src);

      c.toBlob(function (b) {
        if (b) return resolve(b);
        // Trình duyệt không mã hoá được WebP -> lùi về JPEG
        c.toBlob(function (b2) {
          b2 ? resolve(b2) : reject(new Error('Không nén được ảnh'));
        }, 'image/jpeg', 0.80);
      }, 'image/webp', chatLuong);
    };
    img.onerror = function () { reject(new Error('File không phải ảnh hợp lệ')); };
    img.src = URL.createObjectURL(file);
  });
}
```

**Quy trình đầy đủ khi admin chọn file:**

```js
async function xuLyChonAnh(file, tenSanPham) {
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) throw new Error('Chỉ nhận JPG, PNG, WebP');

  var slug  = taoSlug(tenSanPham);
  var stamp = Date.now().toString(36);          // ngắn hơn Date.now() thập phân
  var ten   = stamp + '-' + slug + '.webp';

  var chinh = await nenAnh(file, 1200, 0.82);
  var thumb = await nenAnh(file,  400, 0.75);

  await taiAnhLen(chinh, 'san-pham/' + ten);
  await taiAnhLen(thumb, 'thumb/'    + ten);

  return 'san-pham/' + ten;                     // lưu path này vào DB, thumb suy ra bằng replace()
}
```

### 4.2. Đặt tên file

Quy ước: `<timestamp-base36>-<slug-không-dấu>.webp` — ví dụ `mfa8x2k-ke-dien-thoai-rong.webp`

- **Không dấu, không khoảng trắng, không ký tự đặc biệt** — tránh rắc rối URL-encoding.
- **Bất biến**: sửa ảnh thì tạo tên mới, **không bao giờ ghi đè** tên cũ. Đây là điều kiện để cache vĩnh viễn.
- **Cùng tên file cho cả 2 bản**, chỉ khác thư mục (`san-pham/` và `thumb/`) → suy ra URL thumbnail bằng một phép `replace()`, không cần lưu 2 cột trong DB.
- Có slug tên sản phẩm trong tên file thì tốt cho SEO ảnh và dễ tìm khi phải lục thủ công trong Dashboard.

```js
function taoSlug(s) {
  return String(s || 'anh').normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D')
    .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);
}
```

### 4.3. Cache

- **Khi upload**: gửi header `cache-control: max-age=31536000`. Mặc định của Supabase Storage chỉ là `3600` (1 giờ) — đổi thành 1 năm là **đòn bẩy lớn nhất để tiết kiệm egress**, vì tên file bất biến nên không có rủi ro khách thấy ảnh cũ.
- **Trong HTML**: dùng `<img loading="lazy" decoding="async" width="..." height="...">`. Đặt sẵn `width`/`height` để tránh nhảy layout và tránh trình duyệt phải tải xong mới biết chỗ.
- **Trang danh sách chỉ dùng thumbnail.** Không bao giờ đặt ảnh 1200px vào thẻ `<img>` hiển thị 200px — đó là cách đốt quota nhanh nhất.
- **Phân trang / lazy-load danh sách**: đừng render 200 sản phẩm một lúc.
- **Ổ đĩa local**: đổi `setCachePeriod` sang `CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable()` trong `TaiNguyenAnhConfig.java`.

---

## 5. Cảnh báo — những cái KHÔNG nên dùng

### 5.1. ⚠️ Hiểu đúng về quota Supabase (điểm dễ nhầm nhất)

**Không phải "tổng 10 GB băng thông".** Đây là **hai quota riêng biệt, không cộng dồn và không thay thế cho nhau**:
- **5 GB uncached egress** — cache miss
- **5 GB cached egress** — phục vụ qua CDN

Và cả hai là **Unified Egress Quota dùng chung cho MỌI dịch vụ**: Database, Auth, Storage, Edge Functions, Realtime, Log Drains.

Tính thử: ảnh phục vụ qua CDN chủ yếu ăn quota **cached 5 GB**. Ở 200 KB/ảnh ≈ **25.000 lượt tải ảnh/tháng**. Nếu một lượt xem shop load 20 thumbnail 200 KB (= 4 MB) thì chỉ được **~1.250 lượt xem/tháng**. Cùng phép tính đó với thumbnail **40 KB**: 20 ảnh × 40 KB = 800 KB/lượt → **~6.250 lượt xem/tháng**, gấp 5 lần. Đây là lý do mục 4.1 là bắt buộc chứ không phải "nên có".

**Vượt quota thì sao:** Free **không có** overage tính tiền — không sợ bị trừ tiền bất ngờ. Nhưng bạn được **grace period CHỈ MỘT LẦN** (*"you will not receive another grace period and your project will be restricted"*). Sau đó: API trả **402**, database chuyển **read-only**, project có thể bị pause. Chỉ hết khi quota nạp lại đầu chu kỳ thanh toán mới, hoặc nâng gói ngay (Pro từ 25 USD/tháng).

### 5.2. ❌ Base64 / ảnh trong database — tệ ở mọi chiều

Toán học không cho phép: base64 phình **+33%** (tính chất của phép mã hoá, không phải con số cần tra). 300 ảnh × 800 KB = 240 MB thô → **~320 MB = 64% quota DB 500 MB**, chưa kể index/WAL/bảng nghiệp vụ. Chạm trần → **Free project chuyển read-only, tức mất luôn khả năng ghi đơn hàng**, không chỉ mất ảnh.

Kể cả nén xuống 250 KB/ảnh thì 400 ảnh vẫn ăn ~133 MB ≈ **27% quota DB** — trong khi cùng số ảnh đó chỉ chiếm **~10% quota Storage 1 GB**. Thêm nữa: mỗi lần đọc ảnh là truy vấn DB **không qua CDN** → trừ vào quota **uncached** vốn còn phải chia cho toàn bộ API nghiệp vụ. Trang danh sách trả JSON hàng chục MB, không cache được từng ảnh, không lazy-load được.

*Chỉ chấp nhận được nếu tổng số ảnh dưới ~30 và mỗi ảnh dưới 100 KB (icon/logo).*

### 5.3. ❌ imgbb — ToS cấm thương mại + xoá tài khoản không báo trước

ToS ghi nguyên văn: *"The Site may not be used in connection with any commercial endeavors except those that are specifically endorsed or approved by us"*, và cấm dùng site *"for any revenue-generating endeavor or commercial enterprise"*. Shop bán hàng rơi đúng vào diện bị cấm.

Nặng hơn: *"WE MAY TERMINATE YOUR USE … OR DELETE YOUR ACCOUNT AND ANY CONTENT OR INFORMATION THAT YOU POSTED AT ANY TIME, WITHOUT WARNING, IN OUR SOLE DISCRETION"*. Toàn bộ ảnh sản phẩm có thể biến mất không báo trước. Không chấp nhận được khi ảnh sản phẩm là tài sản của shop.

### 5.4. ❌ Catbox.moe — bị cấm đích danh đúng use case của bạn

FAQ ghi nguyên văn: *"You cannot use Catbox for commercial services without prior approval. Examples include: a CDN; image host for your business/ecommerce site"*. FAQ có nói lệnh cấm này chỉ áp dụng cho file bị **hotlink** ra ngoài Catbox — nhưng trang bán hàng nhúng `<img src>` tới `files.catbox.moe` **chính là hotlink**, nên ngoại lệ đó không cứu được. Loại ngay từ vòng điều khoản, không cần bàn tới kỹ thuật.

### 5.5. ❌ Uploadcare — "Personal use only" ngay trên trang giá

Trang pricing chính thức ghi thẳng **"Personal use only"** trên gói Free. Cộng thêm 1.000 operations/tháng với định nghĩa "operation" rất rộng (mỗi upload, mỗi transformation, mỗi webhook đi ra, mỗi API request — riêng xoá nền tốn 750 operations). Vượt hạn mức là **ngưng dịch vụ tới đầu tháng sau** → ảnh sản phẩm biến mất khỏi trang bán hàng. Đường thoát rẻ nhất là Pro **66 USD/tháng**.

### 5.6. ⚠️ GitHub + jsDelivr — hợp pháp về băng thông, nhưng ToS cấm đúng cách bạn định dùng

jsDelivr ToS xác nhận nguyên văn *"free for both personal and commercial use, there are no limits on bandwidth or number of requests"* — điều này **đúng**. Nhưng ToS cũng cấm *"Abusing the Service and its resources or using jsDelivr CDN as a general-purpose file or media hosting service"*, nêu đích danh ví dụ *"running an image hosting website and using jsDelivr CDN as a storage for all uploaded images"* — gần như trùng khớp với kịch bản của bạn.

GitHub AUP: *"If we determine your bandwidth usage to be significantly excessive … we reserve the right to suspend your Account, throttle your file hosting, or otherwise limit your activity"*. Docs GitHub khuyên thẳng: *"Store programmatically generated files outside of Git, such as in object storage"*.

Kỹ thuật cũng dở: git giữ **vĩnh viễn** mọi phiên bản ảnh → repo phình mãi; xoá ảnh thật sự phải rewrite lịch sử; cache theo `@branch` delay nên phải tag mỗi lần thêm ảnh; admin không rành git thì không dùng nổi.

*Chỉ đáng cân nhắc cho một nhóm nhỏ ảnh "nóng" (banner trang chủ) để giảm tải egress cho Supabase nếu sau này chạm trần.*

### 5.7. ❌ raw.githubusercontent.com — rủi ro không đo được

GitHub changelog 08/05/2025 xác nhận việc siết rate limit áp dụng cho *"downloading files from raw.githubusercontent.com"* với request ẩn danh — mà **khách của bạn chính là request ẩn danh**. Nhưng changelog **không công bố con số**. (Con số 60 request/giờ/IP là của REST API, **không** có tài liệu nào xác nhận nó áp cho raw.githubusercontent — `[CHƯA XÁC MINH]`.)

Rủi ro không đo được còn tệ hơn một con số xấu đã biết: bạn không có cách nào tính trước lúc nào ảnh sẽ vỡ hàng loạt. Cộng thêm trả `Content-Type: text/plain`, không có CDN edge ở VN. Chỉ dùng để test vài ảnh mẫu lúc phát triển.

### 5.8. ⚠️ Cloudinary — mạnh nhưng hậu quả vượt quota nặng hơn tưởng

Là ứng viên kỹ thuật tốt (không cần thẻ — đã xác minh; upload thẳng từ browser; CDN + `f_auto,q_auto` tự tối ưu). Nhưng bài hỗ trợ chính thức *"What happens if I exceed plan limits?"* cho biết: vượt ~90% có cảnh báo, vượt 100% bị yêu cầu giảm dùng hoặc nâng cấp, và **nếu tiếp tục vượt sau nhiều lần nhắc thì tài khoản có thể bị TỰ ĐỘNG VÔ HIỆU HÓA** (mất truy cập Media Library, API, SDK). Mở lại phải chọn gói trả phí — rẻ nhất **89–99 USD/tháng**.

Thêm nữa: 25 credits là pool **dùng chung** storage + bandwidth + transformations nên khó dự đoán, và đo theo **cửa sổ trượt 30 ngày** nên một đợt traffic đột biến còn ảnh hưởng suốt 30 ngày sau. Nếu vẫn chọn: đặt cảnh báo credit ở **70%**, cố định 2–3 biến thể transformation, đừng sinh URL transformation tuỳ hứng (mỗi biến thể mới tốn credit và derived asset cũng chiếm storage).

### 5.9. ❌ Cloudflare R2 / Backblaze B2 — vướng ràng buộc "không thẻ"

Cả hai đều tốt hơn Supabase về hạn mức (R2: 10 GB + egress miễn phí; B2: 10 GB vĩnh viễn) nhưng:
- **R2**: billing policy Cloudflare ghi *"Ensure that you are using a valid payment method before changing your plan type or enabling subscriptions"* và *"Cloudflare may preauthorize your credit card at any point in a billing period"*. Nhiều báo cáo người dùng xác nhận phải gắn phương thức thanh toán. `[CHƯA XÁC MINH DỨT ĐIỂM]` — trang marketing lại ghi "no credit card required". **Nếu bạn thử bấm Enable R2 và thấy KHÔNG cần thẻ thì R2 lập tức là ứng viên rất mạnh** nhờ egress miễn phí.
- **B2**: bucket **private** miễn phí, nhưng bucket **public** (thứ bạn cần) đòi xác minh email + **lịch sử thanh toán** hoặc trả một khoản nhỏ qua form thẻ.

Cả hai còn cần backend ký presigned URL (không upload thẳng từ browser được) và cần domain riêng để có CDN. Ghi lại làm **đường lùi**: nếu shop đông khách và Supabase liên tục chạm trần egress, R2 là nơi chuyển sang hợp lý nhất — rẻ hơn nhiều so với nâng Supabase Pro 25 USD/tháng chỉ để lấy băng thông.

### 5.10. ❌ Koyeb — free tier đã đóng, đừng mất thời gian thử

Trang giá ngày 17/08/2026 chỉ còn Pro **$29/mo**, Scale $299/mo, Enterprise từ $1000/mo. **Không còn gói Free/Starter nào cho người dùng mới.** Mistral AI công bố mua Koyeb ngày 17/02/2026 và chuyển hướng sang GPU/AI inference. Đăng ký bị pre-auth $29 và tính tiền pro-rata ngay tháng đầu.

*(Docs FAQ của Koyeb vẫn mô tả free instance 512MB RAM — đó là tài liệu chưa cập nhật. Khi hai nguồn của cùng một hãng đá nhau, **trang giá là nguồn quyết định**.)*

### 5.11. ⚠️ Về Spring Boot — đính chính so với khảo sát cũ

`pom.xml` hiện dùng `spring-boot-starter-parent` **3.3.4**, `java.version=17`. Theo endoflife.date (tra 17/08/2026): 3.3 hết hỗ trợ OSS 30/06/2025, 3.4 hết 31/12/2025, **3.5 ĐÃ HẾT hỗ trợ OSS 30/06/2026**. Nhánh còn được vá miễn phí hiện nay chỉ còn **4.0** (hết 31/12/2026) và **4.1** (hết 31/07/2027).

→ **Mục tiêu nâng cấp đúng là Spring Boot 4.1, không phải 3.5.** Tin tốt: docs Spring Boot 4.0 ghi *"requires at least Java 17 and is compatible with versions up to and including Java 26"* nên **Java 17 của bạn là đủ**; chỉ cần biết Boot 4 dùng Tomcat 11.0.x / Servlet 6.1.

*(Lưu ý: CVE-2024-38816/38819 về path traversal chỉ ảnh hưởng `RouterFunctions` của WebMvc.fn/WebFlux.fn, **không** ảnh hưởng `addResourceHandlers` mà `TaiNguyenAnhConfig` đang dùng.)*

---

## 6. Checklist triển khai

**Giai đoạn 1 — làm ngay (local, ~1 buổi)**

- [ ] Đổi DB sang lưu **path tương đối**, thêm hàm `urlAnh()` ở frontend
- [ ] Thêm xác thực cho `POST /api/anh`
- [ ] Thêm hàm `nenAnh()` + `taoSlug()` vào trang admin, sinh cả bản 1200px và 400px
- [ ] Re-encode bằng `ImageIO` + header `nosniff` + `@ExceptionHandler(MaxUploadSizeExceededException.class)`
- [ ] `setCachePeriod` → `maxAge(365 ngày).immutable()`
- [ ] Thêm `spring.servlet.multipart.file-size-threshold=1MB` và `server.tomcat.max-swallow-size=-1`

**Giai đoạn 2 — trước khi deploy (~30 phút)**

- [ ] Tạo bucket `anh-san-pham` (public, 2 MB, chỉ jpeg/png/webp) bằng Dashboard
- [ ] Vào `Storage → Settings` **xem tận mắt** global file size limit
- [ ] Tạo policy `anon_upload_anh` — **hoặc** (khuyên hơn) thêm forward qua Spring Boot bằng `sb_secret_` trong biến môi trường
- [ ] Đổi hàm upload của trang admin từ `POST /api/anh` sang `taiAnhLen()`
- [ ] Đẩy ảnh cũ trong `D:\3d\3d-backend\data\anh` lên bucket, cập nhật cột path trong DB
- [ ] Bật cron ping DB hằng ngày (GitHub Actions / UptimeRobot)
- [ ] Giữ `data\anh` làm kho ảnh gốc + backup, đưa vào quy trình sao lưu (database đã nằm trên Supabase)

**Theo dõi hằng tháng**

- [ ] Xem trang Organization Usage của Supabase: **Storage size** và **Egress** tính theo tổ chức, riêng **Database 500 MB** tính theo từng project
- [ ] Nếu cached egress chạm ~70%: giảm số ảnh trên trang danh sách, hạ thumbnail xuống 320px, hoặc bắt đầu tính chuyện chuyển sang R2