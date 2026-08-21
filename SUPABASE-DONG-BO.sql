-- ============================================================
-- IN3D Shop — Đồng bộ schema Supabase với backend Java
-- Chạy trong Supabase Dashboard → SQL Editor
-- Soạn 2026-08-18, sửa 2026-08-19
--
-- CHẠY LẠI BAO NHIÊU LẦN CŨNG ĐƯỢC.
-- Bản trước dùng "create policy" trơn nên chạy lần hai là báo
-- 'policy "khach_tao_don" for table "don_hang" already exists' rồi dừng giữa chừng.
-- Bản này drop trước khi create, bảng màu thì chỉ thêm màu chưa có.
--
-- Gồm 4 phần, bôi đen hết rồi bấm Run một lần:
--   PHẦN 1 — SỬA GẤP: khôi phục 3 bảng bị hỏng sau khi xoá bảng profiles
--   PHẦN 2 — Thêm created_at / updated_at / is_deleted cho mọi bảng
--   PHẦN 3 — Tạo bảng màu sắc, khuyến mãi, bài viết và các bảng còn thiếu;
--            bỏ don_hang.user_id (auth.users), nối sang nguoi_dung.id
--   PHẦN 4 — Quyền truy cập (RLS)
--
-- CHẠY THẾ NÀO: mở Supabase → SQL Editor → dán cả file → Ctrl+A → Run.
-- Chạy xong kéo xuống dưới cùng xem bảng kết quả, phải thấy đủ 12 bảng
-- trong đó có khuyen_mai và bai_viet.
-- ============================================================


-- ============================================================
-- PHẦN 1 — SỬA GẤP
-- Bảng profiles đã bị xoá, nhưng các RLS policy của don_hang,
-- don_hang_chi_tiet, thanh_toan vẫn trỏ tới nó. Hậu quả: MỌI truy vấn
-- vào 3 bảng đó đều lỗi 'relation "public.profiles" does not exist'
-- ============================================================

-- 1.1. Xoá toàn bộ chính sách đang trỏ tới profiles
do $$
declare r record;
begin
  for r in
    select schemaname, tablename, policyname
    from pg_policies
    where schemaname = 'public'
      and (coalesce(qual::text, '') like '%profiles%'
        or coalesce(with_check::text, '') like '%profiles%')
  loop
    execute format('drop policy %I on %I.%I', r.policyname, r.schemaname, r.tablename);
    raise notice 'Đã xoá policy % trên bảng %', r.policyname, r.tablename;
  end loop;
end $$;

-- 1.2. Cho khách vãng lai GHI đơn hàng (không cho đọc đơn của người khác)
--      drop trước để chạy lại lần hai không báo "already exists"
drop policy if exists "khach_tao_don"       on public.don_hang;
drop policy if exists "khach_tao_chi_tiet"  on public.don_hang_chi_tiet;
drop policy if exists "khach_tao_thanh_toan" on public.thanh_toan;

create policy "khach_tao_don" on public.don_hang
  for insert to anon with check (true);
create policy "khach_tao_chi_tiet" on public.don_hang_chi_tiet
  for insert to anon with check (true);
create policy "khach_tao_thanh_toan" on public.thanh_toan
  for insert to anon with check (true);

-- LƯU Ý QUAN TRỌNG:
-- Trang khách hiện gọi .insert().select('id') nên còn cần quyền ĐỌC don_hang.
-- Mở quyền đọc cho anon = ai cũng xem được tên, số điện thoại, địa chỉ của mọi khách.
-- Cách đúng là viết một hàm security definer để tạo đơn. Chỉ làm khi thật sự
-- chuyển sang phương án "không deploy backend" — hiện đơn đi qua backend Java nên chưa cần.


-- ============================================================
-- PHẦN 2 — Cột chung cho MỌI bảng
-- created_at: lúc tạo | updated_at: lần sửa gần nhất | is_deleted: xoá mềm
-- Toàn hệ thống KHÔNG xoá cứng, chỉ bật is_deleted = true
-- ============================================================

do $$
declare t text;
begin
  foreach t in array array[
    'san_pham', 'danh_muc', 'nguoi_dung',
    'don_hang', 'don_hang_chi_tiet', 'thanh_toan'
  ] loop
    if exists (select 1 from information_schema.tables
               where table_schema = 'public' and table_name = t) then
      execute format('alter table public.%I add column if not exists created_at timestamptz default now()', t);
      execute format('alter table public.%I add column if not exists updated_at timestamptz default now()', t);
      execute format('alter table public.%I add column if not exists is_deleted boolean not null default false', t);
      execute format('create index if not exists idx_%s_is_deleted on public.%I (is_deleted)', t, t);
      raise notice 'Đã thêm cột chung cho bảng %', t;
    end if;
  end loop;
end $$;

-- Tự động cập nhật updated_at mỗi lần sửa
create or replace function public.tu_dong_cap_nhat_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array[
    'san_pham', 'danh_muc', 'nguoi_dung',
    'don_hang', 'don_hang_chi_tiet', 'thanh_toan'
  ] loop
    if exists (select 1 from information_schema.tables
               where table_schema = 'public' and table_name = t) then
      execute format('drop trigger if exists trg_%s_updated_at on public.%I', t, t);
      execute format('create trigger trg_%s_updated_at before update on public.%I
                      for each row execute function public.tu_dong_cap_nhat_updated_at()', t, t);
    end if;
  end loop;
end $$;


-- ============================================================
-- PHẦN 3 — Bảng màu sắc + các bảng backend Java đang dùng
-- (Supabase hiện thiếu vat_tu, nha_cung_cap, ma_otp, mau_sac, khuyen_mai, bai_viet)
-- ============================================================

create table if not exists public.mau_sac (
  id          bigserial primary key,
  ten         text        not null,
  ma_mau      varchar(20),
  ghi_chu     text,
  thu_tu      integer     not null default 0,
  created_at  timestamptz default now(),
  updated_at  timestamptz default now(),
  is_deleted  boolean     not null default false
);

create table if not exists public.nha_cung_cap (
  id          bigserial primary key,
  ten         text        not null,
  lien_he     text,
  ghi_chu     text,
  created_at  timestamptz default now(),
  updated_at  timestamptz default now(),
  is_deleted  boolean     not null default false
);

create table if not exists public.vat_tu (
  id               bigserial primary key,
  ten              text        not null,
  loai             text        not null default 'khac',   -- may_in | nhua | phu_kien | khac
  mau              text,
  mau_sac_id       bigint      references public.mau_sac(id),
  gia              bigint      not null default 0,
  so_luong         integer     not null default 1,
  khoi_luong_gram  integer     not null default 0,
  da_dung_gram     integer     not null default 0,
  trang_thai       varchar(40) not null default 'thanh_cong',
  hinh_anh         text,
  nha_cung_cap_id  bigint      references public.nha_cung_cap(id),
  ghi_chu          text,
  created_at       timestamptz default now(),
  updated_at       timestamptz default now(),
  is_deleted       boolean     not null default false
);

-- ------------------------------------------------------------
-- KHUYẾN MÃI — hai kiểu:
--   kieu_ap_dung = 'don_hang'  : khách nhập MÃ ở giỏ hàng, giảm trên tổng đơn
--   kieu_ap_dung = 'san_pham'  : giảm thẳng vào giá món, TỰ ĐỘNG, không cần mã
-- ------------------------------------------------------------
create table if not exists public.khuyen_mai (
  id            bigserial primary key,
  ma            varchar(40) unique,                        -- null với khuyến mãi sản phẩm
  ten           text        not null,
  mo_ta         text,
  kieu_ap_dung  varchar(20) not null default 'don_hang',   -- don_hang | san_pham
  loai          varchar(20) not null default 'phan_tram',  -- phan_tram | so_tien | mien_ship
  gia_tri       bigint      not null default 0,
  giam_toi_da   bigint      not null default 0,            -- trần giảm cho loại %, 0 = không chặn
  don_toi_thieu bigint      not null default 0,
  chi_khach_moi boolean     not null default false,        -- chỉ người chưa từng đặt đơn nào
  dieu_kien_dia_chi text,                                  -- địa chỉ phải chứa 1 trong các từ khoá
  san_pham_ids  text,                                      -- "12,15,18"; rỗng = mọi sản phẩm
  bat_dau       date,
  ket_thuc      date,
  so_luong      integer     not null default 0,            -- 0 = không giới hạn lượt
  da_dung       integer     not null default 0,
  ap_dung_cho   varchar(20) not null default 'tat_ca',     -- tat_ca | san_pham | dich_vu
  hoat_dong     boolean     not null default true,
  hien_thi      boolean     not null default true,
  created_at    timestamptz default now(),
  updated_at    timestamptz default now(),
  is_deleted    boolean     not null default false
);

-- Bảng đã tạo từ bản SQL trước thì bổ sung cột mới và bỏ ràng buộc NOT NULL của mã
alter table public.khuyen_mai add column if not exists kieu_ap_dung varchar(20) not null default 'don_hang';
alter table public.khuyen_mai add column if not exists san_pham_ids text;
alter table public.khuyen_mai add column if not exists chi_khach_moi boolean not null default false;
alter table public.khuyen_mai add column if not exists dieu_kien_dia_chi text;
alter table public.khuyen_mai alter column ma drop not null;

-- Đơn hàng ghi lại mã đã dùng và số tiền đã giảm
alter table public.don_hang add column if not exists ma_khuyen_mai varchar(40);
alter table public.don_hang add column if not exists tien_giam bigint not null default 0;
-- Tiền giảm do khuyến mãi SẢN PHẨM (tách khỏi tien_giam của mã đơn hàng)
alter table public.don_hang add column if not exists tien_giam_san_pham bigint not null default 0;

-- ------------------------------------------------------------
-- NỐI ĐƠN HÀNG VỚI TÀI KHOẢN DO BACKEND JAVA QUẢN LÝ
-- Cột cũ don_hang.user_id là uuid trỏ sang auth.users của Supabase Auth —
-- di tích từ thời định dùng Supabase Auth, backend Java không hề ghi vào đó.
-- Thay bằng nguoi_dung_id kiểu số, khoá ngoại thẳng sang bảng nguoi_dung.
-- ------------------------------------------------------------
alter table public.don_hang add column if not exists nguoi_dung_id bigint;

do $$
begin
  if not exists (select 1 from information_schema.table_constraints
                 where constraint_name = 'fk_don_hang_nguoi_dung' and table_schema = 'public') then
    alter table public.don_hang
      add constraint fk_don_hang_nguoi_dung
      foreign key (nguoi_dung_id) references public.nguoi_dung(id);
  end if;
end $$;

create index if not exists idx_don_hang_nguoi_dung on public.don_hang (nguoi_dung_id);

-- Bỏ hẳn cột uuid cũ. Cột này luôn rỗng vì backend Java chưa từng ghi vào,
-- nên xoá không mất dữ liệu nào. Chạy sau cùng để lỡ có gì còn tra lại được.
alter table public.don_hang drop column if exists user_id;

-- Từng dòng hàng nhớ giá gốc để hiện "199.000đ (giá gốc 290.000đ)"
-- Lưu ý: don_gia là giá SAU giảm vì cột thanh_tien trong Supabase tự tính = don_gia * so_luong
alter table public.don_hang_chi_tiet add column if not exists don_gia_goc bigint not null default 0;
update public.don_hang_chi_tiet set don_gia_goc = don_gia where don_gia_goc = 0;

-- Bài viết chia sẻ kiến thức in 3D (khối cuối trang chủ)
create table if not exists public.bai_viet (
  id          bigserial primary key,
  tieu_de     text        not null,
  duong_dan   text        unique,
  tom_tat     text,
  noi_dung    text,
  hinh_anh    text,
  tac_gia     text,
  chuyen_muc  varchar(40) not null default 'huong-dan',
  luot_xem    integer     not null default 0,
  hien_thi    boolean     not null default true,
  thu_tu      integer     not null default 0,
  created_at  timestamptz default now(),
  updated_at  timestamptz default now(),
  is_deleted  boolean     not null default false
);

create table if not exists public.ma_otp (
  id          bigserial primary key,
  email       text        not null,
  ma          varchar(10) not null,
  het_han     timestamptz not null,
  da_dung     boolean     not null default false,
  so_lan_sai  integer     not null default 0,
  created_at  timestamptz default now(),
  updated_at  timestamptz default now(),
  is_deleted  boolean     not null default false
);

-- Cột trạng thái + loại sản phẩm (nếu bảng san_pham có từ trước mà chưa có)
alter table public.san_pham add column if not exists trang_thai varchar(40) not null default 'san_hang';
alter table public.san_pham add column if not exists loai_san_pham varchar(30) not null default 'ban';

create index if not exists idx_vat_tu_is_deleted     on public.vat_tu (is_deleted);
create index if not exists idx_mau_sac_is_deleted    on public.mau_sac (is_deleted);
create index if not exists idx_ncc_is_deleted        on public.nha_cung_cap (is_deleted);
create index if not exists idx_bai_viet_hien_thi     on public.bai_viet (hien_thi, is_deleted);
create index if not exists idx_khuyen_mai_hoat_dong  on public.khuyen_mai (hoat_dong, is_deleted);
create index if not exists idx_khuyen_mai_kieu       on public.khuyen_mai (kieu_ap_dung);
-- ma đã có unique index sẵn nên không cần đánh index thêm

-- Trigger updated_at cho các bảng mới
do $$
declare t text;
begin
  foreach t in array array['mau_sac', 'nha_cung_cap', 'vat_tu', 'ma_otp', 'bai_viet', 'khuyen_mai'] loop
    execute format('drop trigger if exists trg_%s_updated_at on public.%I', t, t);
    execute format('create trigger trg_%s_updated_at before update on public.%I
                    for each row execute function public.tu_dong_cap_nhat_updated_at()', t, t);
  end loop;
end $$;

-- Bảng màu mặc định (khớp với dữ liệu backend Java tự nạp).
-- Bản trước dùng "on conflict do nothing" nhưng cột ten KHÔNG có ràng buộc duy nhất
-- nên chẳng chặn được gì: chạy file hai lần là có 24 màu. Đổi sang chỉ thêm màu chưa có.
insert into public.mau_sac (ten, ma_mau, thu_tu)
select m.ten, m.ma_mau, m.thu_tu
from (values
  ('Đỏ', '#e03131', 1), ('Vàng', '#f5b400', 2), ('Đen', '#1c1c1c', 3),
  ('Trắng', '#f8f9fa', 4), ('Be', '#e0cda9', 5), ('Xám', '#868e96', 6),
  ('Xanh lá', '#2f9e44', 7), ('Xanh dương', '#1971c2', 8), ('Cam', '#f76707', 9),
  ('Hồng', '#e64980', 10), ('Tím', '#7048e8', 11), ('Trong suốt', '#dee2e6', 12)
) as m(ten, ma_mau, thu_tu)
where not exists (
  select 1 from public.mau_sac c where lower(c.ten) = lower(m.ten)
);


-- ============================================================
-- PHẦN 4 — QUYỀN TRUY CẬP (RLS)
-- Bật RLS cho các bảng quản trị: mặc định KHÔNG có policy nào cho anon
-- => chỉ backend Java (dùng user postgres, bỏ qua RLS) đọc/ghi được
-- ============================================================

alter table public.mau_sac      enable row level security;
alter table public.nha_cung_cap enable row level security;
alter table public.vat_tu       enable row level security;
alter table public.ma_otp       enable row level security;
alter table public.bai_viet     enable row level security;
alter table public.khuyen_mai   enable row level security;

drop policy if exists "ai_cung_xem_mau"        on public.mau_sac;
drop policy if exists "ai_cung_doc_bai_viet"   on public.bai_viet;
drop policy if exists "ai_cung_doc_khuyen_mai" on public.khuyen_mai;

-- Trang bán hàng chỉ cần ĐỌC màu để hiển thị
create policy "ai_cung_xem_mau" on public.mau_sac
  for select to anon using (is_deleted = false);

-- Bài viết là nội dung công khai: cho đọc bài đang bật, không cho ghi
create policy "ai_cung_doc_bai_viet" on public.bai_viet
  for select to anon using (is_deleted = false and hien_thi = true);

-- Khuyến mãi: chỉ cho khách ĐỌC chương trình đang chạy để khoe ở trang chủ.
-- Tuyệt đối không mở quyền ghi — sửa được da_dung là dùng mã hết lượt vô tư.
create policy "ai_cung_doc_khuyen_mai" on public.khuyen_mai
  for select to anon using (is_deleted = false and hoat_dong = true and hien_thi = true);


-- ============================================================
-- KIỂM TRA LẠI SAU KHI CHẠY
-- Cột nào cũng phải là true, và phải thấy đủ các bảng:
-- bai_viet, danh_muc, don_hang, don_hang_chi_tiet, khuyen_mai, ma_otp,
-- mau_sac, nguoi_dung, nha_cung_cap, san_pham, thanh_toan, vat_tu
-- ============================================================
select table_name,
       bool_or(column_name = 'created_at') as co_created_at,
       bool_or(column_name = 'updated_at') as co_updated_at,
       bool_or(column_name = 'is_deleted') as co_is_deleted
from information_schema.columns
where table_schema = 'public'
group by table_name
order by table_name;
