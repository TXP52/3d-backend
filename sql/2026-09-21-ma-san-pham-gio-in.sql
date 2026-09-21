-- ============================================================
-- 2026-09-21 — Mã sản phẩm tự đặt, thời gian in, cài đặt chi phí máy in
--
-- Chỉ THÊM, không xoá gì; backend bản cũ vẫn chạy được sau khi chạy file này
-- (cột mới để NULL / có mặc định nên lệnh INSERT cũ không vướng).
--
--   1. san_pham.ma_san_pham   — mã chủ shop tự đặt; để trống thì backend sinh SP-<số tăng dần>.
--                               Sản phẩm cũ nhận đúng mã đang hiện trên web: SP-<id>.
--   2. bien_the.thoi_gian_in_phut — thời gian in MỘT cái (phút), để tính khấu hao máy + tiền điện.
--   3. cai_dat                — bảng khoá/giá trị cho các định mức chi phí máy in (sửa ở trang quản trị).
-- ============================================================

begin;

-- ---------- 1. Mã sản phẩm ----------
alter table san_pham add column if not exists ma_san_pham varchar(40);
update san_pham set ma_san_pham = 'SP-' || id where ma_san_pham is null;
-- Không phân biệt hoa thường, chỉ tính sản phẩm chưa xoá (xoá mềm rồi thì mã dùng lại được)
create unique index if not exists san_pham_ma_uq on san_pham (upper(ma_san_pham)) where not is_deleted;

-- ---------- 2. Thời gian in ----------
alter table bien_the add column if not exists thoi_gian_in_phut integer not null default 0;

-- ---------- 3. Cài đặt chi phí máy ----------
create table if not exists cai_dat (
    khoa       text        primary key,
    gia_tri    text        not null,
    mo_ta      text,
    updated_at timestamptz not null default now()
);

commit;
