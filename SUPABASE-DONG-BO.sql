-- ============================================================
-- IN3D Shop — Đồng bộ schema Supabase với backend Java
-- Chạy trong Supabase Dashboard → SQL Editor
-- Ngày soạn: 2026-08-18
--
-- Gồm 3 phần, chạy lần lượt từ trên xuống:
--   PHẦN 1 — SỬA GẤP: khôi phục 3 bảng bị hỏng sau khi xoá bảng profiles
--   PHẦN 2 — Thêm created_at / updated_at / is_deleted cho mọi bảng
--   PHẦN 3 — Tạo bảng màu sắc và các bảng còn thiếu
-- ============================================================


-- ============================================================
-- PHẦN 1 — SỬA GẤP
-- Bảng profiles đã bị xoá, nhưng các RLS policy của don_hang,
-- don_hang_chi_tiet, thanh_toan vẫn trỏ tới nó. Hậu quả: MỌI truy vấn
-- vào 3 bảng đó đều lỗi 'relation "public.profiles" does not exist'
-- ============================================================

-- 1.1. Xem chính sách nào đang trỏ tới profiles (chạy để biết, không bắt buộc)
select tablename, policyname, qual::text, with_check::text
from pg_policies
where schemaname = 'public'
  and (coalesce(qual::text, '') like '%profiles%'
    or coalesce(with_check::text, '') like '%profiles%');

-- 1.2. Xoá toàn bộ chính sách đang trỏ tới profiles
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

-- 1.3. Cho khách vãng lai GHI đơn hàng (không cho đọc đơn của người khác)
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
-- (Supabase hiện thiếu vat_tu, nha_cung_cap, ma_otp, mau_sac)
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

-- don_hang.user_id đang là uuid trỏ sang auth.users (Supabase Auth) trong khi backend Java
-- dùng bảng nguoi_dung với id kiểu số. Muốn nối đơn hàng với tài khoản do backend Java quản lý:
--   alter table public.don_hang add column if not exists nguoi_dung_id bigint references public.nguoi_dung(id);
-- Giữ nguyên user_id để dữ liệu cũ không mất. Chỉ chạy khi đã chốt dùng backend Java.

create index if not exists idx_vat_tu_is_deleted   on public.vat_tu (is_deleted);
create index if not exists idx_mau_sac_is_deleted  on public.mau_sac (is_deleted);
create index if not exists idx_ncc_is_deleted      on public.nha_cung_cap (is_deleted);

-- Trigger updated_at cho các bảng mới
do $$
declare t text;
begin
  foreach t in array array['mau_sac', 'nha_cung_cap', 'vat_tu', 'ma_otp'] loop
    execute format('drop trigger if exists trg_%s_updated_at on public.%I', t, t);
    execute format('create trigger trg_%s_updated_at before update on public.%I
                    for each row execute function public.tu_dong_cap_nhat_updated_at()', t, t);
  end loop;
end $$;

-- Bảng màu mặc định (khớp với dữ liệu backend Java tự nạp)
insert into public.mau_sac (ten, ma_mau, thu_tu) values
  ('Đỏ', '#e03131', 1), ('Vàng', '#f5b400', 2), ('Đen', '#1c1c1c', 3),
  ('Trắng', '#f8f9fa', 4), ('Be', '#e0cda9', 5), ('Xám', '#868e96', 6),
  ('Xanh lá', '#2f9e44', 7), ('Xanh dương', '#1971c2', 8), ('Cam', '#f76707', 9),
  ('Hồng', '#e64980', 10), ('Tím', '#7048e8', 11), ('Trong suốt', '#dee2e6', 12)
on conflict do nothing;

-- Bật RLS cho các bảng quản trị: mặc định KHÔNG có policy nào cho anon
-- => chỉ backend Java (dùng user postgres, bỏ qua RLS) đọc/ghi được
alter table public.mau_sac      enable row level security;
alter table public.nha_cung_cap enable row level security;
alter table public.vat_tu       enable row level security;
alter table public.ma_otp       enable row level security;

-- Trang bán hàng chỉ cần ĐỌC màu để hiển thị
create policy "ai_cung_xem_mau" on public.mau_sac
  for select to anon using (is_deleted = false);


-- ============================================================
-- KIỂM TRA LẠI SAU KHI CHẠY
-- ============================================================
select table_name,
       bool_or(column_name = 'created_at') as co_created_at,
       bool_or(column_name = 'updated_at') as co_updated_at,
       bool_or(column_name = 'is_deleted') as co_is_deleted
from information_schema.columns
where table_schema = 'public'
group by table_name
order by table_name;
