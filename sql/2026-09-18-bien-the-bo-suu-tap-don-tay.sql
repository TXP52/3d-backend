-- ============================================================
-- 2026-09-18 — Biến thể sản phẩm, bộ sưu tập, đơn tạo tay, khách hàng nhập tay
--
-- CHẠY MỘT LẦN trên Supabase (SQL Editor hoặc script sb.py). Chỉ THÊM, không xoá
-- cột hay bảng nào đang có, nên backend bản cũ vẫn chạy bình thường sau khi chạy file này.
--
-- Tóm tắt:
--   1. bien_the            — mỗi sản phẩm có nhiều biến thể (Đỏ, Xám...); giá/tồn kho/ảnh/trạng thái riêng
--   2. san_pham_vat_tu     — lô nhựa gắn vào BIẾN THỂ thay vì sản phẩm
--   3. bo_suu_tap          — bộ sưu tập (chủ đề), một sản phẩm nằm được nhiều bộ
--   4. don_hang.kenh       — bán qua đâu: website / facebook / zalo / tai_shop
--   5. don_hang_chi_tiet   — nhớ khách mua biến thể nào (kèm tên lúc mua)
--   6. nguoi_dung          — cho phép thêm khách không có email / chưa có mật khẩu
-- ============================================================

begin;

-- ---------- 1. Biến thể ----------
create table if not exists bien_the (
    id            bigserial primary key,
    san_pham_id   bigint      not null references san_pham(id),
    ten           text,                                   -- null = biến thể mặc định của sản phẩm không phân loại
    mau_sac_id    bigint      references mau_sac(id),      -- null = không theo màu / phối nhiều màu
    ma_sku        text,
    gia           bigint,                                 -- null = lấy giá sản phẩm
    ton_kho       integer     not null default 0,
    trang_thai    varchar(30),                            -- null = theo trạng thái sản phẩm
    danh_sach_anh text,                                   -- mỗi dòng một ảnh, ảnh đầu là ảnh đại diện biến thể
    so_luong      integer     not null default 0,         -- tổng số cái đã in
    nhieu_mau     boolean     not null default false,
    mac_dinh      boolean     not null default false,
    thu_tu        integer     not null default 0,
    is_deleted    boolean     not null default false,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
create index if not exists bien_the_san_pham_idx on bien_the (san_pham_id) where not is_deleted;
-- mỗi sản phẩm đúng MỘT biến thể mặc định
create unique index if not exists bien_the_mac_dinh_uq on bien_the (san_pham_id) where mac_dinh and not is_deleted;

-- Dữ liệu cũ: mỗi sản phẩm thành một biến thể mặc định, mang theo tồn kho / số lượng in / nhiều màu
insert into bien_the (san_pham_id, ten, ton_kho, so_luong, nhieu_mau, mac_dinh, thu_tu, is_deleted, created_at, updated_at)
select sp.id, null, sp.ton_kho, sp.so_luong, sp.nhieu_mau, true, 0, sp.is_deleted, sp.created_at, sp.updated_at
from san_pham sp
where not exists (select 1 from bien_the b where b.san_pham_id = sp.id);

-- ---------- 2. Lô nhựa gắn vào biến thể ----------
alter table san_pham_vat_tu add column if not exists bien_the_id bigint references bien_the(id);
update san_pham_vat_tu n
set bien_the_id = b.id
from bien_the b
where b.san_pham_id = n.san_pham_id and b.mac_dinh and n.bien_the_id is null;
create index if not exists san_pham_vat_tu_bien_the_idx on san_pham_vat_tu (bien_the_id);

-- ---------- 3. Bộ sưu tập ----------
create table if not exists bo_suu_tap (
    id         bigserial primary key,
    ten        text        not null,
    duong_dan  text        not null,
    mo_ta      text,
    hinh_anh   text,
    hien_thi   boolean     not null default true,
    thu_tu     integer     not null default 0,
    is_deleted boolean     not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
-- đường dẫn chỉ cần duy nhất trong các bộ CHƯA xoá (xoá mềm rồi thì tên cũ dùng lại được)
create unique index if not exists bo_suu_tap_duong_dan_uq on bo_suu_tap (duong_dan) where not is_deleted;

create table if not exists san_pham_bo_suu_tap (
    bo_suu_tap_id bigint  not null references bo_suu_tap(id),
    san_pham_id   bigint  not null references san_pham(id),
    thu_tu        integer not null default 0,
    primary key (bo_suu_tap_id, san_pham_id)
);
create index if not exists san_pham_bo_suu_tap_sp_idx on san_pham_bo_suu_tap (san_pham_id);

-- ---------- 4. Kênh bán ----------
alter table don_hang add column if not exists kenh varchar(20) not null default 'website';

-- ---------- 5. Đơn nhớ biến thể ----------
alter table don_hang_chi_tiet add column if not exists bien_the_id  bigint references bien_the(id);
alter table don_hang_chi_tiet add column if not exists ten_bien_the text;

-- ---------- 6. Khách nhập tay ở trang quản trị ----------
-- Khách mua tại shop / Facebook / Zalo thường không có email và không cần mật khẩu.
-- Email vẫn là duy nhất, nhưng Postgres cho phép nhiều dòng email NULL.
alter table nguoi_dung alter column email drop not null;
alter table nguoi_dung alter column mat_khau_hash drop not null;
alter table nguoi_dung add column if not exists ghi_chu text;
create index if not exists nguoi_dung_sdt_idx on nguoi_dung (so_dien_thoai) where not is_deleted;

commit;
