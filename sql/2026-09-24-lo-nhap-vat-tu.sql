-- ============================================================
-- 2026-09-24 — Đợt nhập hàng của vật tư (mỗi đợt một giá)
--
-- Một vật tư (ví dụ "Nhựa PLA trắng") mua nhiều lần, mỗi lần một giá. Trước đây
-- vat_tu chỉ có MỘT ô gia + MỘT ô so_luong nên nhập đợt mới là phải sửa đè giá cũ,
-- không còn biết đợt nào mua bao nhiêu.
--
-- Cách tính: BÌNH QUÂN GIA QUYỀN. Các đợt cộng dồn vào một kho chung;
--   vat_tu.so_luong = tổng số lượng các đợt
--   vat_tu.tien_mua = tổng tiền các đợt (chính xác, không làm tròn)
--   vat_tu.gia      = đơn giá bình quân = tien_mua / so_luong (làm tròn, chỉ để hiển thị)
-- Backend ghi lại ba cột này mỗi lần đợt nhập thay đổi, nên mọi chỗ tính vốn / tiền
-- nhựa đã dùng đang đọc vat_tu vẫn chạy y như cũ.
--
-- Chỉ THÊM, không xoá gì.
--   1. lo_nhap        — mỗi dòng một đợt nhập của một vật tư.
--   2. vat_tu.tien_mua — tổng tiền đã mua (cũ: gia × so_luong, nay cộng từ các đợt).
--   3. Backfill       — mỗi vật tư đang có thành MỘT đợt nhập, đúng số đang lưu.
-- ============================================================

begin;

-- ---------- 1. Bảng đợt nhập ----------
create table if not exists lo_nhap (
    id              bigserial   primary key,
    vat_tu_id       bigint      not null references vat_tu (id),
    ngay_nhap       date        not null default current_date,
    so_luong        integer     not null default 1,
    gia             bigint      not null default 0,   -- đơn giá MỘT đơn vị ở đợt này
    nha_cung_cap_id bigint      references nha_cung_cap (id),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

-- Đọc luôn theo vật tư, xếp đợt cũ trước
create index if not exists lo_nhap_vat_tu_idx on lo_nhap (vat_tu_id, ngay_nhap, id);

-- ---------- 2. Tổng tiền mua của vật tư ----------
alter table vat_tu add column if not exists tien_mua bigint;

-- ---------- 3. Vật tư đang có -> một đợt nhập ----------
insert into lo_nhap (vat_tu_id, ngay_nhap, so_luong, gia, nha_cung_cap_id, created_at, updated_at)
select v.id,
       coalesce(v.created_at::date, current_date),
       greatest(coalesce(v.so_luong, 0), 0),
       coalesce(v.gia, 0),
       v.nha_cung_cap_id,
       coalesce(v.created_at, now()),
       now()
from vat_tu v
where not exists (select 1 from lo_nhap l where l.vat_tu_id = v.id);

update vat_tu set tien_mua = coalesce(gia, 0) * coalesce(so_luong, 0) where tien_mua is null;

commit;
