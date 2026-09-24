-- ============================================================
-- 2026-09-24 — Đơn hàng: khoản cộng thêm và phí
--
-- Đơn gõ tay ở trang quản trị nay có thêm hai dòng tiền ngoài tiền hàng:
--   phu_thu — CỘNG vào tổng: ship khách trả, gói quà, phụ thu...
--   phi     — TRỪ khỏi tổng: phí sàn (Shopee, TikTok), phí ship shop chịu...
--
--   Tổng tiền đơn = tiền hàng sau giảm giá + phu_thu - phi (không âm)
--
-- Đơn cũ và đơn khách tự đặt trên web đều để 0 nên tổng không đổi.
-- Chỉ THÊM cột, có mặc định 0 nên backend bản cũ vẫn chạy được.
-- ============================================================

begin;

alter table don_hang add column if not exists phu_thu bigint not null default 0;
alter table don_hang add column if not exists phi     bigint not null default 0;

commit;
