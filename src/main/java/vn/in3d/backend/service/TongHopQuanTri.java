package vn.in3d.backend.service;

import org.springframework.stereotype.Service;
import vn.in3d.backend.entity.BaiViet;
import vn.in3d.backend.entity.DanhMuc;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.entity.DonHangChiTiet;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.entity.MauSac;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.entity.NhaCungCap;
import vn.in3d.backend.entity.ThanhToan;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SỐ LIỆU TỔNG HỢP cho các trang quản trị — trước đây mỗi trang tự cộng trừ bằng JS
 * trên cả danh sách đơn / sản phẩm / kho tải về (apex.js tinhThongKe, tinhKhachHang,
 * tinhChiPhi, và KPI của từng trang).
 *
 * Tính TRONG BỘ NHỚ từ bộ nhớ đệm (BoNhoDem), không hỏi database. Dữ liệu vài chục
 * dòng nên tính lại mỗi request vẫn dưới 1 ms, khỏi phải giữ thêm một tầng đệm.
 *
 * Luật chung:
 *   - Doanh thu KHÔNG cộng đơn đã huỷ (da_huy): đơn huỷ không mang về đồng nào.
 *     Số đơn thì đếm hết vì đó là số đơn đã nhận.
 *   - Năm / tháng của đơn tính theo giờ Việt Nam (Asia/Ho_Chi_Minh), không theo
 *     múi giờ của máy chạy backend hay trình duyệt.
 *   - Mọi danh sách giữ đúng thứ tự của bộ dữ liệu tương ứng trong khoi-tao.
 */
@Service
public class TongHopQuanTri {

    public static final ZoneId GIO_VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Tồn kho sản phẩm từ mức này trở xuống là "sắp hết" (quy ước hiển thị cũ của apex.js). */
    public static final int NGUONG_SAP_HET = 5;
    /** Cuộn nhựa còn từ 200g trở xuống là sắp hết — trùng VatTu.NGUONG_NHUA_SAP_HET. */
    public static final int NGUONG_NHUA_SAP_HET = 200;
    /** Tỉ lệ lãi mặc định của trang Quản lý vốn (%). */
    public static final double TI_LE_LAI_MAC_DINH = 120;

    private static final DateTimeFormatter NGAY_VN = DateTimeFormatter.ofPattern("d/M/yyyy");

    /** Tên trạng thái đơn, đúng thứ tự DonHang.TRANG_THAI_HOP_LE. */
    private static final Map<String, String> TEN_TRANG_THAI_DON = new LinkedHashMap<>();
    static {
        TEN_TRANG_THAI_DON.put("cho_xac_nhan", "Chờ xác nhận");
        TEN_TRANG_THAI_DON.put("dang_xu_ly", "Đang xử lý");
        TEN_TRANG_THAI_DON.put("dang_giao", "Đang giao");
        TEN_TRANG_THAI_DON.put("hoan_thanh", "Hoàn thành");
        TEN_TRANG_THAI_DON.put("da_huy", "Đã huỷ");
    }

    /** Trạng thái sản phẩm theo quy trình in, đúng thứ tự ô lọc ở trang Sản phẩm. */
    private static final List<String> TRANG_THAI_SAN_PHAM = List.of(
            "du_kien", "da_dat", "dang_in", "da_in", "san_hang", "dang_van_chuyen", "thanh_cong", "hoan_hang", "het_hang");

    /** Trạng thái vật tư, đúng thứ tự ô lọc ở trang Kho. */
    private static final List<String> TRANG_THAI_VAT_TU = List.of(
            "con_hang", "sap_het", "het_hang", "da_dat", "dang_van_chuyen");

    private final BoNhoDem boNho;

    public TongHopQuanTri(BoNhoDem boNho) {
        this.boNho = boNho;
    }

    /* ============================================================
       dem — số trên menu
       ============================================================ */

    public Map<String, Object> dem() {
        int cho = 0;
        for (DonHang d : boNho.dsDonHang()) if ("cho_xac_nhan".equals(d.getTrangThai())) cho++;
        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("donChoXacNhan", cho);
        return ra;
    }

    /* ============================================================
       tong-quan — index.html
       ============================================================ */

    public Map<String, Object> tongQuan() {
        List<DonHang> don = boNho.dsDonHang();
        List<Map<String, Object>> sp = boNho.dsSanPham(true);

        int choXacNhan = 0;
        for (DonHang d : don) if ("cho_xac_nhan".equals(d.getTrangThai())) choXacNhan++;
        int dangBan = 0;
        for (Map<String, Object> p : sp) if (Boolean.TRUE.equals(p.get("dangBan"))) dangBan++;

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("doanhThu", doanhThu(don));
        kpi.put("tongDon", don.size());
        kpi.put("choXacNhan", choXacNhan);
        kpi.put("soSanPham", sp.size());
        kpi.put("soSanPhamDangBan", dangBan);

        List<Map<String, Object>> ganNhat = new ArrayList<>();
        for (DonHang d : don.subList(0, Math.min(6, don.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("maDon", d.getMaDon());
            m.put("tenKhach", d.getTenKhach());
            m.put("trangThai", d.getTrangThai());
            m.put("trangThaiTen", tenTrangThaiDon(d.getTrangThai()));
            m.put("tongTien", tienDon(d));
            ganNhat.add(m);
        }

        // Giữ đúng luật cũ của thẻ "Sắp hết hàng": mọi sản phẩm chưa xoá có tồn kho <= 5, lấy 6 cái đầu
        List<Map<String, Object>> sapHet = new ArrayList<>();
        int tongSapHet = 0;
        for (Map<String, Object> p : sp) {
            int ton = soNguyen(p.get("tonKho"));
            if (ton > NGUONG_SAP_HET) continue;
            tongSapHet++;
            if (sapHet.size() >= 6) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.get("id"));
            m.put("ten", p.get("ten"));
            m.put("hinhAnh", p.get("hinhAnh"));
            m.put("tonKho", ton);
            m.put("mucTon", mucTon(ton));
            m.put("loaiSanPham", p.get("loaiSanPham"));
            m.put("dangBan", p.get("dangBan"));
            sapHet.add(m);
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        ra.put("theoThang", theoThang(don));
        ra.put("theoTrangThai", theoTrangThaiGapTruoc(don));
        ra.put("donGanNhat", ganNhat);
        ra.put("sapHet", sapHet);
        ra.put("tongSapHet", tongSapHet);
        ra.put("nguongSapHet", NGUONG_SAP_HET);
        return ra;
    }

    /* ============================================================
       bao-cao — bao-cao.html
       ============================================================ */

    public Map<String, Object> baoCao() {
        List<DonHang> don = boNho.dsDonHang();
        long doanhThu = doanhThu(don);
        int khongHuy = 0, hoanThanh = 0;
        for (DonHang d : don) {
            if (!laHuy(d)) khongHuy++;
            if ("hoan_thanh".equals(d.getTrangThai())) hoanThanh++;
        }

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("doanhThu", doanhThu);
        kpi.put("soDon", don.size());
        kpi.put("soDonKhongHuy", khongHuy);
        // Giá trị trung bình trên các đơn có doanh thu (đơn huỷ không có doanh thu thì không chia vào)
        kpi.put("giaTriTrungBinh", khongHuy == 0 ? 0L : Math.round((double) doanhThu / khongHuy));
        kpi.put("soDonHoanThanh", hoanThanh);
        kpi.put("tiLeHoanThanh", don.isEmpty() ? 0.0 : Math.round(hoanThanh * 1000.0 / don.size()) / 10.0);

        // Tỉ trọng doanh thu theo trạng thái: bỏ đơn huỷ (không phải doanh thu)
        List<Map<String, Object>> tiTrong = new ArrayList<>();
        for (Map<String, Object> tt : theoTrangThaiGapTruoc(don)) {
            if ("da_huy".equals(tt.get("trangThai"))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("trangThai", tt.get("trangThai"));
            m.put("trangThaiTen", tt.get("trangThaiTen"));
            m.put("doanhThu", tt.get("tongTien"));
            tiTrong.add(m);
        }

        List<Map<String, Object>> bang = new ArrayList<>();
        for (DonHang d : don) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("maDon", d.getMaDon());
            m.put("tenKhach", d.getTenKhach());
            m.put("createdAt", d.getCreatedAt());
            m.put("ngay", d.getCreatedAt() == null ? "" : d.getCreatedAt().atZoneSameInstant(GIO_VN).format(NGAY_VN));
            m.put("trangThai", d.getTrangThai());
            m.put("trangThaiTen", tenTrangThaiDon(d.getTrangThai()));
            m.put("tongTien", tienDon(d));
            bang.add(m);
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        ra.put("theoThang", theoThang(don));
        ra.put("theoTrangThai", theoTrangThaiGapTruoc(don));
        ra.put("tiTrongDoanhThu", tiTrong);
        ra.put("don", bang);
        return ra;
    }

    /* ============================================================
       khach-hang — khach-hang.html (nơi gọi đã kiểm tra token admin)
       ============================================================ */

    /**
     * Khách trong bảng nguoi_dung (tự đăng ký HOẶC chủ shop nhập tay) ghép với đơn theo
     * don_hang.nguoi_dung_id (bản JS cũ ghép theo HỌ TÊN nên hai người trùng tên bị gộp).
     * Đơn không gắn tài khoản nào (hoặc gắn tài khoản đã xoá) mà số điện thoại trùng ĐÚNG MỘT
     * khách trong bảng (so sau khi bỏ dấu cách / chấm / gạch, +84 coi như 0) thì tính cho
     * khách đó — nên bấm "Thêm khách vào danh bạ" ở một dòng khách lẻ không đẻ ra dòng trùng.
     * Còn lại là khách vãng lai, gom theo số điện thoại (không có số thì theo tên).
     * Khách trong bảng trước (id tăng dần, kể cả người CHƯA có đơn nào), khách vãng lai
     * sau (mua gần nhất trước). laTaiKhoan = có dòng trong nguoi_dung nên sửa / xoá được;
     * coMatKhau = khách tự đăng ký (có mật khẩu, email là tên đăng nhập), false = khách chủ
     * shop nhập tay vào danh bạ hoặc khách vãng lai.
     */
    public Map<String, Object> khachHang() {
        List<DonHang> don = boNho.dsDonHang();
        Map<Long, Map<String, Object>> theoTaiKhoan = new LinkedHashMap<>();
        // Số điện thoại (đã chuẩn hoá) -> các dòng khách trong bảng mang số đó. Bỏ tài khoản
        // quản trị: đơn chủ shop tự đặt thử bằng số của mình không phải khách mua
        Map<String, List<Map<String, Object>>> theoSdt = new HashMap<>();
        int coMatKhau = 0;
        for (NguoiDung u : boNho.dsNguoiDung()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("khoa", "nd-" + u.getId());
            m.put("nguoiDungId", u.getId());
            m.put("ten", u.getHoTen());
            m.put("email", u.getEmail());
            m.put("soDienThoai", trongThanhNull(u.getSoDienThoai()));
            m.put("nhom", "admin".equals(u.getVaiTro()) ? "quan_tri" : "khach_hang");
            m.put("nhomTen", "admin".equals(u.getVaiTro()) ? "Quản trị" : "Khách hàng");
            m.put("soDon", 0);
            m.put("tongChi", 0L);
            m.put("donDauTien", null);
            m.put("donGanNhat", null);
            m.put("ngayDangKy", u.getCreatedAt());
            m.put("diaChi", trongThanhNull(u.getDiaChi()));
            m.put("ghiChu", trongThanhNull(u.getGhiChu()));
            m.put("laTaiKhoan", true);
            boolean matKhau = trongThanhNull(u.getMatKhauHash()) != null;
            m.put("coMatKhau", matKhau);
            if (matKhau) coMatKhau++;
            theoTaiKhoan.put(u.getId(), m);
            String sdt = chuanSoDienThoai(u.getSoDienThoai());
            if (sdt != null && !"admin".equals(u.getVaiTro())) {
                theoSdt.computeIfAbsent(sdt, k -> new ArrayList<>()).add(m);
            }
        }

        Map<String, Map<String, Object>> vangLai = new LinkedHashMap<>();
        for (DonHang d : don) {   // mới nhất trước
            Map<String, Object> m = d.getNguoiDungId() == null ? null : theoTaiKhoan.get(d.getNguoiDungId());
            if (m == null) {
                // Đơn không gắn tài khoản: số điện thoại trùng đúng MỘT khách trong bảng thì tính
                // cho khách đó (trùng từ hai khách trở lên thì không đoán, để là khách vãng lai)
                List<Map<String, Object>> cungSo = theoSdt.get(chuanSoDienThoai(d.getSoDienThoai()));
                if (cungSo != null && cungSo.size() == 1) m = cungSo.get(0);
            }
            if (m != null) {
                if (m.get("soDienThoai") == null) m.put("soDienThoai", trongThanhNull(d.getSoDienThoai()));
            } else {
                String sdt = trongThanhNull(d.getSoDienThoai());
                String khoa = sdt != null ? "sdt-" + sdt
                        : "ten-" + (d.getTenKhach() == null ? "" : d.getTenKhach().trim().toLowerCase(Locale.ROOT));
                m = vangLai.get(khoa);
                if (m == null) {
                    m = new LinkedHashMap<>();
                    m.put("khoa", khoa);
                    m.put("nguoiDungId", null);
                    m.put("ten", d.getTenKhach());       // tên ở đơn gần nhất
                    m.put("email", null);
                    m.put("soDienThoai", sdt);
                    m.put("nhom", "khach_le");
                    m.put("nhomTen", "Khách lẻ");
                    m.put("soDon", 0);
                    m.put("tongChi", 0L);
                    m.put("donDauTien", null);
                    m.put("donGanNhat", null);
                    m.put("ngayDangKy", null);
                    m.put("diaChi", trongThanhNull(d.getDiaChi()));   // địa chỉ ở đơn gần nhất
                    m.put("ghiChu", null);
                    m.put("laTaiKhoan", false);                       // chưa có dòng trong nguoi_dung
                    m.put("coMatKhau", false);
                    vangLai.put(khoa, m);
                }
            }
            m.put("soDon", (Integer) m.get("soDon") + 1);
            if (!laHuy(d)) m.put("tongChi", (Long) m.get("tongChi") + tienDon(d));
            OffsetDateTime luc = d.getCreatedAt();
            if (luc != null) {
                OffsetDateTime dau = (OffsetDateTime) m.get("donDauTien");
                OffsetDateTime cuoi = (OffsetDateTime) m.get("donGanNhat");
                if (dau == null || luc.isBefore(dau)) m.put("donDauTien", luc);
                if (cuoi == null || luc.isAfter(cuoi)) m.put("donGanNhat", luc);
            }
        }

        List<Map<String, Object>> khach = new ArrayList<>(theoTaiKhoan.values());
        khach.addAll(vangLai.values());
        long tongChi = 0;
        for (Map<String, Object> m : khach) tongChi += (Long) m.get("tongChi");

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("tongKhach", khach.size());
        // Tài khoản khách TỰ đăng ký (có mật khẩu) tách khỏi khách chủ shop nhập tay vào danh bạ
        kpi.put("daDangKy", coMatKhau);
        kpi.put("danhBa", theoTaiKhoan.size() - coMatKhau);
        kpi.put("vangLai", vangLai.size());
        kpi.put("tongChiTieu", tongChi);

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        ra.put("khach", khach);
        return ra;
    }

    /* ============================================================
       von — quan-ly-von.html
       ============================================================ */

    /** Chi phí tính từ kho — cùng công thức Apex.tinhChiPhi cũ. */
    public record ChiPhi(long tienMuaVatTu, long tienMayIn, long tienNhuaTong, long tienNhuaDaDung,
                         long tongChiPhi, long tongGramDaDung, long tongGramMua, long gramConLai,
                         double giaNhuaTrungBinhMoiGram) {
        public Map<String, Object> thanhMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tienMuaVatTu", tienMuaVatTu);
            m.put("tienMayIn", tienMayIn);
            m.put("tienNhuaTong", tienNhuaTong);
            m.put("tienNhuaDaDung", tienNhuaDaDung);
            m.put("tongChiPhi", tongChiPhi);
            m.put("tongGramDaDung", tongGramDaDung);
            m.put("tongGramMua", tongGramMua);
            m.put("gramConLai", gramConLai);
            m.put("giaNhuaTrungBinhMoiGram", giaNhuaTrungBinhMoiGram);
            return m;
        }
    }

    /** Tự lấy bản chụp kho (một lượt) — cho nơi gọi chỉ cần chi phí. */
    public ChiPhi chiPhi() {
        return chiPhi(boNho.dsVatTu());
    }

    /**
     * Chi phí tính trên MỘT bản chụp kho nơi gọi đã lấy sẵn: nơi nào vừa cần chi phí
     * vừa cần chính danh sách kho thì truyền vào, khỏi hai lượt đọc rơi vào hai thế hệ
     * khác nhau (tổng tiền không khớp với mấy dòng in ngay bên cạnh).
     */
    public ChiPhi chiPhi(List<Map<String, Object>> dsVatTu) {
        long tienMua = 0, tienMayIn = 0, tienNhuaTong = 0, tienNhuaDaDung = 0, gramDaDung = 0, gramMua = 0;
        for (Map<String, Object> v : dsVatTu) {
            long tongTienMua = so(v.get("tongTienMua"));
            tienMua += tongTienMua;
            if ("nhua".equals(v.get("loai"))) {
                tienNhuaDaDung += so(v.get("tienDaDung"));
                gramDaDung += so(v.get("daDungGram"));
                gramMua += so(v.get("khoiLuongGram")) * so(v.get("soLuong"));
                tienNhuaTong += tongTienMua;
            } else if ("may_in".equals(v.get("loai"))) {
                tienMayIn += tongTienMua;
            }
        }
        return new ChiPhi(tienMua, tienMayIn, tienNhuaTong, tienNhuaDaDung, tienMua + tienNhuaDaDung,
                gramDaDung, gramMua, Math.max(0, gramMua - gramDaDung),
                gramMua > 0 ? (double) tienNhuaTong / gramMua : 0);
    }

    /** Tỉ lệ lãi không hợp lệ (thiếu, âm, không phải số) thì dùng mặc định 120%. */
    public static double chuanTiLeLai(Double tiLeLai) {
        return tiLeLai == null || tiLeLai.isNaN() || tiLeLai.isInfinite() || tiLeLai < 0 ? TI_LE_LAI_MAC_DINH : tiLeLai;
    }

    public Map<String, Object> von(Double tiLeLaiGui) {
        double tiLeLai = chuanTiLeLai(tiLeLaiGui);
        // MỘT bản chụp kho cho cả phần chi phí lẫn bảng cuộn nhựa bên dưới
        List<Map<String, Object>> vt = boNho.dsVatTu();
        ChiPhi cp = chiPhi(vt);
        long doanhThu = doanhThu(boNho.dsDonHang());

        List<Map<String, Object>> cuon = new ArrayList<>();
        for (Map<String, Object> v : vt) {
            if (!"nhua".equals(v.get("loai"))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.get("id"));
            m.put("ten", v.get("ten"));
            m.put("mau", v.get("mau"));
            m.put("maMau", v.get("maMau"));
            m.put("donGiaMoiGram", v.get("donGiaMoiGram"));
            m.put("daDungGram", soNguyen(v.get("daDungGram")));
            m.put("tongGram", soNguyen(v.get("khoiLuongGram")) * soNguyen(v.get("soLuong")));
            m.put("conLaiGram", soNguyen(v.get("conLaiGram")));
            m.put("tienDaDung", so(v.get("tienDaDung")));
            cuon.add(m);
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("chiPhi", cp.thanhMap());
        ra.put("doanhThu", doanhThu);
        ra.put("laiLo", doanhThu - cp.tongChiPhi());
        ra.put("tiLeLai", tiLeLai);
        ra.put("giaVonMoiGram", Math.round(cp.giaNhuaTrungBinhMoiGram()));
        ra.put("giaBanMoiGram", Math.round(giaBanMoiGram(cp, tiLeLai)));
        ra.put("cuonNhua", cuon);
        return ra;
    }

    /**
     * Giá dự kiến các mẫu in chủ shop tự nhập (tên + số gram). CHỈ TÍNH, không lưu gì.
     * tienNhua = gram × giá nhựa trung bình; giaBan = gram × giá bán/gram, làm tròn tới 500đ;
     * lai = giaBan − tiền nhựa (chưa làm tròn) rồi mới làm tròn — y như trang cũ.
     */
    public Map<String, Object> giaMauIn(List<?> mauIn, Double tiLeLaiGui) {
        double tiLeLai = chuanTiLeLai(tiLeLaiGui);
        ChiPhi cp = chiPhi();
        double giaBanGram = giaBanMoiGram(cp, tiLeLai);

        List<Map<String, Object>> ds = new ArrayList<>();
        for (Object o : mauIn == null ? List.of() : mauIn) {
            if (!(o instanceof Map<?, ?> mau)) continue;
            long gram = so(mau.get("gram"));
            double tienNhua = gram * cp.giaNhuaTrungBinhMoiGram();
            long giaBan = Math.round(gram * giaBanGram / 500) * 500;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ten", mau.get("ten") == null ? "" : String.valueOf(mau.get("ten")));
            m.put("gram", gram);
            m.put("tienNhua", Math.round(tienNhua));
            m.put("giaBan", giaBan);
            m.put("lai", Math.round(giaBan - tienNhua));
            ds.add(m);
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("tiLeLai", tiLeLai);
        ra.put("giaVonMoiGram", Math.round(cp.giaNhuaTrungBinhMoiGram()));
        ra.put("giaBanMoiGram", Math.round(giaBanGram));
        ra.put("mauIn", ds);
        return ra;
    }

    private static double giaBanMoiGram(ChiPhi cp, double tiLeLai) {
        return cp.giaNhuaTrungBinhMoiGram() * (1 + tiLeLai / 100);
    }

    /* ============================================================
       tk-san-pham — san-pham.html
       ============================================================ */

    public Map<String, Object> tkSanPham() {
        List<Map<String, Object>> sp = boNho.dsSanPham(true);
        int dangHien = 0, dangIn = 0, chuaPhanLoai = 0;
        long giaTriTon = 0;
        Map<String, Object> theoTrangThai = new LinkedHashMap<>();
        for (String k : TRANG_THAI_SAN_PHAM) theoTrangThai.put(k, 0);
        Map<Long, Integer> theoDanhMucId = new HashMap<>();
        List<Map<String, Object>> theoSanPham = new ArrayList<>();

        for (Map<String, Object> p : sp) {
            if (Boolean.TRUE.equals(p.get("dangBan"))) dangHien++;
            if ("dang_in".equals(p.get("trangThai"))) dangIn++;
            Object tt = p.get("trangThai");
            if (tt != null && theoTrangThai.containsKey(tt)) theoTrangThai.put((String) tt, (Integer) theoTrangThai.get(tt) + 1);
            Long dm = soHoacNull(p.get("danhMucId"));
            if (dm == null) chuaPhanLoai++;
            else theoDanhMucId.merge(dm, 1, Integer::sum);

            int ton = soNguyen(p.get("tonKho"));
            long giaTri = giaTriTonKho(p, ton);
            giaTriTon += giaTri;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.get("id"));
            m.put("giaTriTon", giaTri);
            m.put("mucTon", mucTon(ton));
            theoSanPham.add(m);
        }

        List<Map<String, Object>> theoDanhMuc = new ArrayList<>();
        for (DanhMuc d : boNho.danhMuc().danhSach()) {
            if ("vat_tu".equals(d.getNhom())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("danhMucId", d.getId());
            m.put("ten", d.getTen());
            m.put("soSanPham", theoDanhMucId.getOrDefault(d.getId(), 0));
            theoDanhMuc.add(m);
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("tong", sp.size());
        ra.put("dangHien", dangHien);
        ra.put("dangIn", dangIn);
        ra.put("giaTriTon", giaTriTon);
        ra.put("theoTrangThai", theoTrangThai);
        ra.put("theoDanhMuc", theoDanhMuc);
        ra.put("chuaPhanLoai", chuaPhanLoai);
        ra.put("theoSanPham", theoSanPham);
        return ra;
    }

    /**
     * Giá trị tồn của một sản phẩm = Σ (giá hiển thị của biến thể × tồn kho biến thể) — mỗi
     * phân loại có thể có giá riêng; cùng công thức tóm tắt ở form sản phẩm (san-pham.html
     * veTomTatSp), tồn âm thì trừ ngược lại y như ở đó. Sản phẩm chưa có biến thể nào (chỉ
     * khi sửa tay trên database) thì giá sản phẩm × tổng tồn như trước.
     */
    private static long giaTriTonKho(Map<String, Object> p, int tongTon) {
        if (!(p.get("bienThe") instanceof List<?> ds) || ds.isEmpty()) return so(p.get("gia")) * tongTon;
        long giaTri = 0;
        for (Object o : ds) {
            if (o instanceof Map<?, ?> bt) giaTri += so(bt.get("giaHienThi")) * soNguyen(bt.get("tonKho"));
        }
        return giaTri;
    }

    /* ============================================================
       tk-kho — kho.html
       ============================================================ */

    public Map<String, Object> tkKho() {
        // MỘT bản chụp kho cho cả chi phí lẫn các bảng bên dưới
        List<Map<String, Object>> vt = boNho.dsVatTu();
        ChiPhi cp = chiPhi(vt);
        int soMayIn = 0;
        long soCuonNhua = 0;
        Map<String, Object> theoTrangThai = new LinkedHashMap<>();
        for (String k : TRANG_THAI_VAT_TU) theoTrangThai.put(k, 0);
        List<Map<String, Object>> sapHet = new ArrayList<>();
        List<Map<String, Object>> theoVatTu = new ArrayList<>();

        for (Map<String, Object> v : vt) {
            if ("may_in".equals(v.get("loai"))) soMayIn++;
            Object tt = v.get("trangThaiTinh");
            if (tt != null && theoTrangThai.containsKey(tt)) theoTrangThai.put((String) tt, (Integer) theoTrangThai.get(tt) + 1);
            if ("nhua".equals(v.get("loai"))) {
                soCuonNhua += so(v.get("soLuong"));
                int con = soNguyen(v.get("conLaiGram"));
                if (con <= NGUONG_NHUA_SAP_HET) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.get("id"));
                    m.put("ten", v.get("ten"));
                    m.put("mau", v.get("mau"));
                    m.put("maMau", v.get("maMau"));
                    m.put("conLaiGram", con);
                    m.put("trangThaiTinh", tt);
                    sapHet.add(m);
                }
            }
            long tong = so(v.get("tongCoThe"));
            long daDung = so(v.get("daDungGram"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.get("id"));
            m.put("phanTramDaDung", tong > 0 ? Math.min(100.0, (double) daDung / tong * 100) : 0.0);
            theoVatTu.add(m);
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("tongVon", cp.tienMuaVatTu());
        ra.put("tienMayIn", cp.tienMayIn());
        ra.put("soMayIn", soMayIn);
        ra.put("soCuonNhua", soCuonNhua);
        ra.put("tongGramMua", cp.tongGramMua());
        ra.put("tongGramDaDung", cp.tongGramDaDung());
        ra.put("gramConLai", cp.gramConLai());
        ra.put("nhuaConItCanhBao", cp.gramConLai() <= NGUONG_NHUA_SAP_HET);
        ra.put("nguongNhuaSapHet", NGUONG_NHUA_SAP_HET);
        ra.put("theoTrangThai", theoTrangThai);
        ra.put("nhuaSapHet", sapHet);
        ra.put("theoVatTu", theoVatTu);
        return ra;
    }

    /* ============================================================
       tk-don-hang — don-hang.html
       ============================================================ */

    public Map<String, Object> tkDonHang() {
        List<DonHang> don = boNho.dsDonHang();
        Map<String, long[]> dem = new LinkedHashMap<>();   // trạng thái -> [số đơn, tổng tiền]
        for (String k : TEN_TRANG_THAI_DON.keySet()) dem.put(k, new long[2]);
        long tongTien = 0;
        List<Map<String, Object>> theoDon = new ArrayList<>();
        for (DonHang d : don) {
            long[] o = dem.get(d.getTrangThai());
            if (o != null) { o[0]++; o[1] += tienDon(d); }
            tongTien += tienDon(d);

            int soMon = 0;
            for (DonHangChiTiet c : d.getChiTiet()) soMon += c.getSoLuong() == null ? 0 : c.getSoLuong();
            ThanhToan tt = d.getThanhToan().isEmpty() ? null : d.getThanhToan().get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("soMon", soMon);
            m.put("phuongThuc", tt == null || tt.getPhuongThuc() == null ? "cod" : tt.getPhuongThuc());
            m.put("daThanhToan", tt != null && "da_thanh_toan".equals(tt.getTrangThai()));
            theoDon.add(m);
        }

        List<Map<String, Object>> theoTrangThai = new ArrayList<>();
        dem.forEach((k, o) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("trangThai", k);
            m.put("trangThaiTen", TEN_TRANG_THAI_DON.get(k));
            m.put("soDon", (int) o[0]);
            m.put("tongTien", o[1]);
            theoTrangThai.add(m);
        });

        Map<String, Object> tong = new LinkedHashMap<>();
        tong.put("soDon", don.size());
        tong.put("tongTien", tongTien);
        tong.put("doanhThu", doanhThu(don));

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("theoTrangThai", theoTrangThai);
        ra.put("tong", tong);
        ra.put("theoDon", theoDon);
        return ra;
    }

    /* ============================================================
       tk-khuyen-mai — khuyen-mai.html
       ============================================================ */

    public Map<String, Object> tkKhuyenMai() {
        List<KhuyenMai> km = boNho.dsKhuyenMai(true);
        List<DonHang> don = boNho.dsDonHang();

        int theoDon = 0, theoSanPham = 0, dangChay = 0;
        for (KhuyenMai k : km) {
            String kieu = k.getKieuApDung() == null || k.getKieuApDung().isEmpty() ? "don_hang" : k.getKieuApDung();
            if ("don_hang".equals(kieu)) theoDon++;
            if ("san_pham".equals(kieu)) theoSanPham++;
            if ("dang_chay".equals(k.getTrangThai())) dangChay++;
        }
        long daGiam = 0;
        for (DonHang d : don) daGiam += d.getTienGiam() + d.getTienGiamSanPham();

        // Hàng BÁN (kể cả đang ẩn) — ô chọn sản phẩm và bảng giá trước / sau
        List<Map<String, Object>> hangBan = new ArrayList<>();
        for (Map<String, Object> p : boNho.dsSanPham(true)) {
            Object loai = p.get("loaiSanPham");
            if (loai == null || "ban".equals(loai)) hangBan.add(p);
        }
        List<Map<String, Object>> sanPhamChon = new ArrayList<>();
        for (Map<String, Object> p : hangBan) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.get("id"));
            m.put("ten", p.get("ten"));
            m.put("gia", so(p.get("gia")));
            sanPhamChon.add(m);
        }

        List<Map<String, Object>> theoKhuyenMai = new ArrayList<>();
        for (KhuyenMai k : km) {
            int soDon = 0;
            long tienGiam = 0;
            if (k.getMa() != null && !k.getMa().isEmpty()) {
                for (DonHang d : don) {
                    if (k.getMa().equals(d.getMaKhuyenMai())) { soDon++; tienGiam += d.getTienGiam(); }
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", k.getId());
            m.put("soDonDaDung", soDon);
            m.put("tienGiamTheoMa", tienGiam);
            // Thanh lượt dùng: % đã dùng, tối đa 100; không giới hạn lượt thì null
            Integer phanTramLuot = null;
            if (k.getSoLuong() > 0) {
                phanTramLuot = (int) Math.min(100L, Math.round((double) k.getDaDung() / k.getSoLuong() * 100));
            }
            m.put("phanTramLuot", phanTramLuot);
            m.put("sanPhamGiam", "san_pham".equals(k.getKieuApDung()) ? sanPhamGiam(k, hangBan) : null);
            theoKhuyenMai.add(m);
        }

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("theoDon", theoDon);
        kpi.put("theoSanPham", theoSanPham);
        kpi.put("dangChay", dangChay);
        kpi.put("tienDaGiam", daGiam);

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        ra.put("theoKhuyenMai", theoKhuyenMai);
        ra.put("sanPhamChon", sanPhamChon);
        return ra;
    }

    /** Sản phẩm một chương trình giảm giá sản phẩm áp vào, kèm giá trước - sau (tối đa 12 dòng). */
    private Map<String, Object> sanPhamGiam(KhuyenMai k, List<Map<String, Object>> hangBan) {
        List<Long> ids = new ArrayList<>();
        if (k.getSanPhamIds() != null) {
            for (String phan : k.getSanPhamIds().split(",")) {
                try { ids.add(Long.parseLong(phan.trim())); } catch (NumberFormatException boQua) { /* bỏ phần không phải số */ }
            }
        }
        List<Map<String, Object>> ds = new ArrayList<>();
        int tong = 0;
        for (Map<String, Object> p : hangBan) {
            if (!ids.isEmpty() && !ids.contains((Long) p.get("id"))) continue;
            tong++;
            if (ds.size() >= 12) continue;
            long gia = so(p.get("gia"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sanPhamId", p.get("id"));
            m.put("ten", p.get("ten"));
            m.put("giaGoc", gia);
            m.put("giaSauGiam", gia - k.tinhTienGiam(gia));
            ds.add(m);
        }
        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("tong", tong);
        ra.put("ds", ds);
        return ra;
    }

    /* ============================================================
       tk-bai-viet — bai-viet.html
       ============================================================ */

    /**
     * Bốn thẻ KPI của trang Bài viết: tổng số bài / đang hiện / đang tắt / tổng lượt đọc.
     * Đếm trên bộ nhớ đệm bài viết (kể cả bài đang tắt) để trang khỏi cộng lại bằng JS.
     */
    public Map<String, Object> tkBaiViet() {
        List<BaiViet> bv = boNho.dsBaiViet(true);
        int dangHien = 0;
        long tongLuotDoc = 0;
        for (BaiViet b : bv) {
            if (Boolean.TRUE.equals(b.getHienThi())) dangHien++;
            tongLuotDoc += b.getLuotXem() == null ? 0 : b.getLuotXem();
        }
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("tongSoBai", bv.size());
        kpi.put("dangHien", dangHien);
        kpi.put("dangTat", bv.size() - dangHien);
        kpi.put("tongLuotDoc", tongLuotDoc);

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        return ra;
    }

    /* ============================================================
       tk-nha-cung-cap, tk-mau-sac, tk-danh-muc, tk-cai-dat
       ============================================================ */

    public Map<String, Object> tkNhaCungCap() {
        List<Map<String, Object>> vt = boNho.dsVatTu();
        int coNguon = 0;
        long tongTienNhap = 0;
        for (Map<String, Object> v : vt) {
            if (so(v.get("nhaCungCapId")) == 0) continue;
            coNguon++;
            tongTienNhap += so(v.get("tongTienMua"));
        }

        List<Map<String, Object>> theoNcc = new ArrayList<>();
        for (NhaCungCap n : boNho.dsNhaCungCap()) {
            List<Map<String, Object>> cua = new ArrayList<>();
            long tien = 0;
            for (Map<String, Object> v : vt) {
                if (!Objects.equals(soHoacNull(v.get("nhaCungCapId")), n.getId())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", v.get("id"));
                m.put("ten", v.get("ten"));
                m.put("mau", v.get("mau"));
                m.put("maMau", v.get("maMau"));
                m.put("tongTienMua", so(v.get("tongTienMua")));
                cua.add(m);
                tien += so(v.get("tongTienMua"));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("soVatTu", cua.size());
            m.put("tongTienNhap", tien);
            m.put("vatTu", cua);
            theoNcc.add(m);
        }

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("soNhaCungCap", boNho.dsNhaCungCap().size());
        kpi.put("soVatTuCoNguon", coNguon);
        kpi.put("tongTienNhap", tongTienNhap);

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        ra.put("theoNhaCungCap", theoNcc);
        return ra;
    }

    public Map<String, Object> tkMauSac() {
        Map<Long, Integer> dem = new HashMap<>();
        for (Map<String, Object> v : boNho.dsVatTu()) {
            Long ms = soHoacNull(v.get("mauSacId"));
            if (ms != null) dem.merge(ms, 1, Integer::sum);
        }
        List<MauSac> dsMau = boNho.dsMauSac();
        int dangDung = 0;
        List<Map<String, Object>> theoMau = new ArrayList<>();
        for (MauSac ms : dsMau) {
            int so = dem.getOrDefault(ms.getId(), 0);
            if (so > 0) dangDung++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ms.getId());
            m.put("soVatTu", so);
            theoMau.add(m);
        }
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("tongSoMau", dsMau.size());
        kpi.put("dangDung", dangDung);
        kpi.put("chuaDung", dsMau.size() - dangDung);

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        ra.put("theoMau", theoMau);
        return ra;
    }

    /**
     * tk-bo-suu-tap — bo-suu-tap.html.
     * tongSanPham đếm số sản phẩm KHÁC NHAU đang nằm trong ít nhất một bộ chưa xoá
     * (một sản phẩm thuộc ba bộ vẫn chỉ tính một).
     */
    public Map<String, Object> tkBoSuuTap() {
        List<Map<String, Object>> bo = boNho.dsBoSuuTap(true);
        int dangHien = 0;
        Set<Object> sanPham = new HashSet<>();
        for (Map<String, Object> b : bo) {
            if (Boolean.TRUE.equals(b.get("hienThi"))) dangHien++;
            if (!(b.get("sanPham") instanceof List<?> ds)) continue;
            for (Object o : ds) {
                if (o instanceof Map<?, ?> sp) sanPham.add(sp.get("id"));
            }
        }
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("tongBo", bo.size());
        kpi.put("dangHien", dangHien);
        kpi.put("tongSanPham", sanPham.size());

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        return ra;
    }

    public Map<String, Object> tkDanhMuc() {
        Map<Long, Integer> soSanPham = new HashMap<>();
        for (Map<String, Object> p : boNho.dsSanPham(true)) {
            Long dm = soHoacNull(p.get("danhMucId"));
            if (dm != null) soSanPham.merge(dm, 1, Integer::sum);
        }
        Map<Long, Integer> soVatTu = new HashMap<>();
        for (Map<String, Object> v : boNho.dsVatTu()) {
            Long dm = soHoacNull(v.get("danhMucId"));
            if (dm != null) soVatTu.merge(dm, 1, Integer::sum);
        }
        int dmSanPham = 0, dmVatTu = 0;
        List<Map<String, Object>> theoDanhMuc = new ArrayList<>();
        for (DanhMuc d : boNho.danhMuc().danhSach()) {
            if ("vat_tu".equals(d.getNhom())) dmVatTu++;
            else dmSanPham++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("nhom", d.getNhom());
            m.put("soSanPham", soSanPham.getOrDefault(d.getId(), 0));
            m.put("soVatTu", soVatTu.getOrDefault(d.getId(), 0));
            theoDanhMuc.add(m);
        }
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("danhMucSanPham", dmSanPham);
        kpi.put("loaiVatTu", dmVatTu);

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("kpi", kpi);
        ra.put("theoDanhMuc", theoDanhMuc);
        return ra;
    }

    public Map<String, Object> tkCaiDat() {
        int dmSanPham = 0, dmVatTu = 0;
        for (DanhMuc d : boNho.danhMuc().danhSach()) {
            if ("vat_tu".equals(d.getNhom())) dmVatTu++;
            else dmSanPham++;
        }
        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("soSanPham", boNho.dsSanPham(true).size());
        ra.put("soDanhMucSanPham", dmSanPham);
        ra.put("soLoaiVatTu", dmVatTu);
        ra.put("soDonHang", boNho.dsDonHang().size());
        return ra;
    }

    /* ============================================================
       Dùng chung
       ============================================================ */

    /** Doanh thu 12 tháng năm nay + năm trước (không cộng đơn huỷ) và số đơn từng tháng năm nay (đếm hết). */
    private Map<String, Object> theoThang(List<DonHang> don) {
        int namNay = ZonedDateTime.now(GIO_VN).getYear();
        long[] dtNamNay = new long[12], dtNamTruoc = new long[12];
        int[] soDon = new int[12];
        boolean coDuLieu = false;
        for (DonHang d : don) {
            if (d.getCreatedAt() == null) continue;
            ZonedDateTime luc = d.getCreatedAt().atZoneSameInstant(GIO_VN);
            int thang = luc.getMonthValue() - 1;
            if (luc.getYear() == namNay) {
                soDon[thang]++;
                if (!laHuy(d)) dtNamNay[thang] += tienDon(d);
                coDuLieu = true;
            } else if (luc.getYear() == namNay - 1) {
                if (!laHuy(d)) dtNamTruoc[thang] += tienDon(d);
                coDuLieu = true;
            }
        }
        List<String> nhan = new ArrayList<>();
        List<Long> a = new ArrayList<>(), b = new ArrayList<>();
        List<Integer> c = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            nhan.add("T" + (i + 1));
            a.add(dtNamNay[i]);
            b.add(dtNamTruoc[i]);
            c.add(soDon[i]);
        }
        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("nam", namNay);
        ra.put("nhan", nhan);
        ra.put("namNay", a);
        ra.put("namTruoc", b);
        ra.put("soDon", c);
        ra.put("coDuLieu", coDuLieu);
        return ra;
    }

    /**
     * Số đơn + tổng tiền theo trạng thái, CHỈ những trạng thái có đơn, xếp theo lần đầu gặp
     * trong danh sách mới-nhất-trước — đúng thứ tự miếng bánh của biểu đồ tròn cũ.
     * tongTien cộng cả đơn huỷ (đây là giá trị đơn theo trạng thái, không phải doanh thu).
     */
    private List<Map<String, Object>> theoTrangThaiGapTruoc(List<DonHang> don) {
        Map<String, Map<String, Object>> gom = new LinkedHashMap<>();
        for (DonHang d : don) {
            String tt = d.getTrangThai() == null ? "" : d.getTrangThai();
            Map<String, Object> m = gom.get(tt);
            if (m == null) {
                m = new LinkedHashMap<>();
                m.put("trangThai", tt);
                m.put("trangThaiTen", tenTrangThaiDon(tt));
                m.put("soDon", 0);
                m.put("tongTien", 0L);
                gom.put(tt, m);
            }
            m.put("soDon", (Integer) m.get("soDon") + 1);
            m.put("tongTien", (Long) m.get("tongTien") + tienDon(d));
        }
        return new ArrayList<>(gom.values());
    }

    private static long doanhThu(List<DonHang> don) {
        long t = 0;
        for (DonHang d : don) if (!laHuy(d)) t += tienDon(d);
        return t;
    }

    private static boolean laHuy(DonHang d) { return "da_huy".equals(d.getTrangThai()); }

    private static long tienDon(DonHang d) { return d.getTongTien() == null ? 0 : d.getTongTien(); }

    private static String tenTrangThaiDon(String tt) {
        return TEN_TRANG_THAI_DON.getOrDefault(tt, tt);
    }

    /** het_hang (<= 0) | sap_het (<= 5) | con_hang — thay cho Apex.badgeTon. */
    private static String mucTon(int ton) {
        if (ton <= 0) return "het_hang";
        if (ton <= NGUONG_SAP_HET) return "sap_het";
        return "con_hang";
    }

    private static long so(Object v) { return v instanceof Number n ? n.longValue() : 0L; }

    private static int soNguyen(Object v) { return v instanceof Number n ? n.intValue() : 0; }

    /** Id kiểu số; null hoặc 0 coi như không có (giống JS kiểm tra "truthy"). */
    private static Long soHoacNull(Object v) {
        return v instanceof Number n && n.longValue() != 0 ? n.longValue() : null;
    }

    private static String trongThanhNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Số điện thoại để SO KHỚP (không để hiển thị): chỉ giữ chữ số, "84..." của +84 đổi
     * thành "0..." — "0912 345 678", "0912.345.678" và "+84 912 345 678" là một người.
     * Không có chữ số nào thì null.
     */
    static String chuanSoDienThoai(String s) {
        if (s == null) return null;
        String so = s.replaceAll("\\D", "");
        if (so.startsWith("84") && so.length() >= 11) so = "0" + so.substring(2);
        return so.isEmpty() ? null : so;
    }
}
