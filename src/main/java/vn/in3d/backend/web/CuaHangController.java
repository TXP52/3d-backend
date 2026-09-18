package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.BoSuuTap;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.service.BoNhoDem;
import vn.in3d.backend.service.KhuyenMaiService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * API cho WEBSITE BÁN HÀNG (công khai, không cần đăng nhập).
 *
 *   GET /api/cua-hang/trang-chu?gioiHan=6          khuyến mãi + sản phẩm + dịch vụ + bài viết trong MỘT lần gọi
 *   GET /api/cua-hang/san-pham[?loai=ban,mau][&boSuuTap=duong-dan][&gioiHan=n]
 *   GET /api/cua-hang/san-pham/{id}                chi tiết + sản phẩm liên quan
 *   GET /api/cua-hang/san-pham/tim?ten=...         cho link cũ chi-tiet.html?ten=
 *   GET /api/cua-hang/bo-suu-tap                   các bộ sưu tập đang khoe
 *   GET /api/cua-hang/bo-suu-tap/{duongDan}        một bộ + sản phẩm của bộ
 *
 * Sản phẩm trả dạng "GỌN": KHÔNG có số liệu giá vốn (cuộn nhựa, gram, tiền nhựa) — mấy
 * thứ đó chỉ trang quản trị được xem. Giá sau khuyến mãi, nhãn trạng thái, chữ giá
 * hiển thị và "đặt được hay không" backend tính sẵn, web khách chỉ việc vẽ.
 *
 * Mỗi sản phẩm kèm danh sách BIẾN THỂ (phân loại) cũng đã tính sẵn giá / nhãn / đặt
 * được hay không cho từng cái, kèm khoảng giá giaTu–giaDen của cả sản phẩm và các bộ
 * sưu tập nó nằm trong. Ảnh của sản phẩm là ảnh sản phẩm GỘP với ảnh của mọi biến thể.
 * Tất cả lấy từ bộ nhớ đệm, không hỏi database.
 */
@RestController
@RequestMapping("/api/cua-hang")
public class CuaHangController {

    /** Nhãn trạng thái cho khách — cùng chữ trang quản trị dùng; lạ hoặc trống thì "Sẵn hàng". */
    private static final Map<String, String> NHAN_TRANG_THAI = Map.of(
            "du_kien", "Dự kiến", "da_dat", "Đã đặt", "dang_in", "Đang in", "da_in", "Đã in",
            "san_hang", "Sẵn hàng", "dang_van_chuyen", "Đang vận chuyển", "thanh_cong", "Thành công",
            "hoan_hang", "Hoàn hàng", "het_hang", "Hết hàng");

    private static final int SO_TRANG_CHU_MAC_DINH = 6;

    private final BoNhoDem boNho;
    private final KhuyenMaiService khuyenMaiService;

    public CuaHangController(BoNhoDem boNho, KhuyenMaiService khuyenMaiService) {
        this.boNho = boNho;
        this.khuyenMaiService = khuyenMaiService;
    }

    /**
     * Trang chủ: { khuyenMai: [<= 3 mã như GET /api/khuyen-mai], sanPham: [hàng bán + hàng mẫu],
     * dichVu: [dịch vụ], baiViet: [bài như GET /api/bai-viet] }. gioiHan <= 0 = lấy hết.
     */
    @GetMapping("/trang-chu")
    public Map<String, Object> trangChu(@RequestParam(required = false) Integer gioiHan) {
        int gh = gioiHan == null ? SO_TRANG_CHU_MAC_DINH : gioiHan;
        List<KhuyenMai> dangChay = kmSanPhamDangChay();

        List<Map<String, Object>> sanPham = new ArrayList<>();
        List<Map<String, Object>> dichVu = new ArrayList<>();
        for (Map<String, Object> sp : boNho.dsSanPham(false)) {
            boolean laDichVu = "dich_vu".equals(sp.get("loaiSanPham"));
            List<Map<String, Object>> vao = laDichVu ? dichVu : sanPham;
            if (gh <= 0 || vao.size() < gh) vao.add(gon(sp, dangChay));
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("khuyenMai", dauDanhSach(boNho.dsKhuyenMai(false), 3));
        ra.put("sanPham", sanPham);
        ra.put("dichVu", dichVu);
        ra.put("baiViet", dauDanhSach(boNho.dsBaiViet(false), gh));
        return ra;
    }

    /**
     * Sản phẩm đang bán theo id tăng dần; ?loai=ban,mau lọc theo loại;
     * ?boSuuTap=duong-dan chỉ lấy hàng trong bộ sưu tập đó (bộ lạ / đang ẩn = danh sách rỗng);
     * gioiHan thiếu hoặc <= 0 = lấy hết.
     */
    @GetMapping("/san-pham")
    public List<Map<String, Object>> sanPham(@RequestParam(required = false) String loai,
                                             @RequestParam(required = false) String boSuuTap,
                                             @RequestParam(required = false) Integer gioiHan) {
        Set<String> cacLoai = new HashSet<>();
        if (loai != null) {
            for (String l : loai.split(",")) if (!l.isBlank()) cacLoai.add(l.trim());
        }
        Set<Long> trongBo = null;
        if (boSuuTap != null && !boSuuTap.isBlank()) {
            BoSuuTap bo = boNho.boSuuTap().theoDuongDan().get(boSuuTap.trim());
            trongBo = new HashSet<>();
            if (bo != null && bo.getHienThi()) {
                for (BoSuuTap.DongSanPham dong : bo.getSanPham()) trongBo.add(dong.getSanPhamId());
            }
        }
        List<KhuyenMai> dangChay = kmSanPhamDangChay();
        List<Map<String, Object>> ra = new ArrayList<>();
        for (Map<String, Object> sp : boNho.dsSanPham(false)) {
            if (!cacLoai.isEmpty() && !cacLoai.contains(String.valueOf(sp.get("loaiSanPham")))) continue;
            if (trongBo != null && !trongBo.contains((Long) sp.get("id"))) continue;
            if (gioiHan != null && gioiHan > 0 && ra.size() >= gioiHan) break;
            ra.add(gon(sp, dangChay));
        }
        return ra;
    }

    /** Chi tiết một sản phẩm đang bán + lienQuan. Không có hoặc đang ẩn -> 404. */
    @GetMapping("/san-pham/{id}")
    public Map<String, Object> chiTiet(@PathVariable Long id) {
        // MỘT bản chụp sản phẩm cho cả món này lẫn danh sách liên quan: hai lượt đọc rời
        // có thể rơi vào hai thế hệ khác nhau (một lệnh ghi chen vào giữa request)
        BoNhoDem.DuLieuSanPham banSp = boNho.sanPham();
        Map<String, Object> sp = banSp.theoId().get(id);
        if (sp == null || Boolean.TRUE.equals(sp.get("daXoa")) || !Boolean.TRUE.equals(sp.get("dangBan"))) {
            throw khongThay();
        }
        return chiTietKemLienQuan(sp, banSp);
    }

    /**
     * Link cũ chi-tiet.html?ten=...: khớp đúng tên (không phân biệt hoa thường) trước,
     * không có thì tên chứa chữ tìm hoặc chữ tìm chứa tên — y như trang chi tiết cũ tự dò.
     */
    @GetMapping("/san-pham/tim")
    public Map<String, Object> timTheoTen(@RequestParam(required = false) String ten) {
        String tim = ten == null ? "" : ten.trim().toLowerCase(Locale.ROOT);
        if (tim.isEmpty()) throw khongThay();
        BoNhoDem.DuLieuSanPham banSp = boNho.sanPham();
        List<Map<String, Object>> ds = BoNhoDem.dsSanPham(banSp, false);
        for (Map<String, Object> sp : ds) {
            if (tenThuong(sp).equals(tim)) return chiTietKemLienQuan(sp, banSp);
        }
        for (Map<String, Object> sp : ds) {
            String t = tenThuong(sp);
            if (!t.isEmpty() && (t.contains(tim) || tim.contains(t))) return chiTietKemLienQuan(sp, banSp);
        }
        throw khongThay();
    }

    /* ---------------- Bộ sưu tập ---------------- */

    /** Các bộ đang khoe, theo thứ tự chủ shop xếp: [{ten, duongDan, moTa, hinhAnh, soSanPham}]. */
    @GetMapping("/bo-suu-tap")
    public List<Map<String, Object>> boSuuTap() {
        BoNhoDem.DuLieuSanPham banSp = boNho.sanPham();
        List<Map<String, Object>> ra = new ArrayList<>();
        for (BoSuuTap b : boNho.boSuuTap().danhSach()) {
            if (!b.getHienThi()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ten", b.getTen());
            m.put("duongDan", b.getDuongDan());
            m.put("moTa", b.getMoTa());
            m.put("hinhAnh", b.getHinhAnh());
            m.put("soSanPham", sanPhamCuaBo(b, banSp).size());
            ra.add(m);
        }
        return ra;
    }

    /**
     * Một bộ sưu tập + hàng của bộ, theo thứ tự chủ shop xếp trong bộ.
     * Bộ không có hoặc đang ẩn -> 404 {loi}.
     */
    @GetMapping("/bo-suu-tap/{duongDan}")
    public Map<String, Object> boSuuTapTheoDuongDan(@PathVariable String duongDan) {
        BoSuuTap b = boNho.boSuuTap().theoDuongDan().get(duongDan);
        if (b == null || !b.getHienThi()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ sưu tập.");
        }
        BoNhoDem.DuLieuSanPham banSp = boNho.sanPham();
        List<KhuyenMai> dangChay = kmSanPhamDangChay();
        List<Map<String, Object>> ds = new ArrayList<>();
        for (Map<String, Object> sp : sanPhamCuaBo(b, banSp)) ds.add(gon(sp, dangChay));

        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("ten", b.getTen());
        ra.put("duongDan", b.getDuongDan());
        ra.put("moTa", b.getMoTa());
        ra.put("hinhAnh", b.getHinhAnh());
        ra.put("soSanPham", ds.size());
        ra.put("sanPham", ds);
        return ra;
    }

    /** Hàng ĐANG BÁN của một bộ, giữ thứ tự chủ shop xếp; hàng ẩn / đã xoá thì bỏ qua. */
    private List<Map<String, Object>> sanPhamCuaBo(BoSuuTap bo, BoNhoDem.DuLieuSanPham banSp) {
        List<Map<String, Object>> ra = new ArrayList<>();
        for (BoSuuTap.DongSanPham dong : bo.getSanPham()) {
            Map<String, Object> sp = banSp.theoId().get(dong.getSanPhamId());
            if (sp == null || Boolean.TRUE.equals(sp.get("daXoa")) || !Boolean.TRUE.equals(sp.get("dangBan"))) continue;
            ra.add(sp);
        }
        return ra;
    }

    /* ---------------- Dựng dữ liệu ---------------- */

    /**
     * Tối đa 4 sản phẩm liên quan: cùng danh mục trước, rồi tới hàng khác; bỏ chính nó và dịch vụ.
     * @param banSp bản chụp sản phẩm nơi gọi đã lấy — chính bản chụp lấy ra sp
     */
    private Map<String, Object> chiTietKemLienQuan(Map<String, Object> sp, BoNhoDem.DuLieuSanPham banSp) {
        List<KhuyenMai> dangChay = kmSanPhamDangChay();
        Object danhMucId = sp.get("danhMucId");
        List<Map<String, Object>> cungDanhMuc = new ArrayList<>();
        List<Map<String, Object>> khac = new ArrayList<>();
        for (Map<String, Object> o : BoNhoDem.dsSanPham(banSp, false)) {
            if (Objects.equals(o.get("id"), sp.get("id")) || "dich_vu".equals(o.get("loaiSanPham"))) continue;
            if (danhMucId != null && danhMucId.equals(o.get("danhMucId"))) cungDanhMuc.add(o);
            else khac.add(o);
        }
        List<Map<String, Object>> lienQuan = new ArrayList<>();
        for (Map<String, Object> o : cungDanhMuc) if (lienQuan.size() < 4) lienQuan.add(gon(o, dangChay));
        for (Map<String, Object> o : khac) if (lienQuan.size() < 4) lienQuan.add(gon(o, dangChay));

        Map<String, Object> ra = gon(sp, dangChay);
        ra.put("lienQuan", lienQuan);
        return ra;
    }

    /**
     * Sản phẩm dạng GỌN cho khách. Giá sau giảm dùng đúng luật của GET /api/khuyen-mai/gia-san-pham
     * và của lúc tạo đơn (KhuyenMaiService.giaSauGiam: chương trình giảm nhiều nhất, hàng "Liên hệ"
     * giá 0 không giảm) — áp cho cả giá sản phẩm lẫn giá từng biến thể, vì khuyến mãi tính theo
     * SẢN PHẨM chứ không theo phân loại.
     */
    private Map<String, Object> gon(Map<String, Object> sp, List<KhuyenMai> dangChay) {
        Long id = (Long) sp.get("id");
        Long giaObj = (Long) sp.get("gia");
        long gia = giaObj == null ? 0 : giaObj;
        String loai = (String) sp.get("loaiSanPham");
        String trangThai = (String) sp.get("trangThai");
        String giaChu = (String) sp.get("giaChu");

        Long giaSauGiam = null, phanTram = null;
        String tenKhuyenMai = null;
        KhuyenMaiService.GiaMon g = giamGia(id, gia, dangChay);
        if (g != null) {
            giaSauGiam = g.giaSauGiam();
            phanTram = phanTram(g);
            tenKhuyenMai = g.khuyenMai() == null ? null : g.khuyenMai().getTen();
        }
        long giaHien = giaSauGiam != null ? giaSauGiam : gia;

        // Ảnh của sản phẩm = ảnh sản phẩm + ảnh của từng biến thể (mặc định trước), bỏ trùng
        Set<String> daCoAnh = new LinkedHashSet<>();
        themAnh(daCoAnh, sp.get("danhSachAnh"));

        boolean sanSangBan = Boolean.TRUE.equals(sp.get("dangBan")) && !"mau".equals(loai);
        List<Map<String, Object>> bienThe = new ArrayList<>();
        long giaTu = Long.MAX_VALUE, giaDen = Long.MIN_VALUE;
        for (Object o : sp.get("bienThe") instanceof List<?> ds ? ds : List.of()) {
            if (!(o instanceof Map<?, ?> tho)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> bt = (Map<String, Object>) tho;
            long giaBt = so(bt.get("giaHienThi"));
            KhuyenMaiService.GiaMon gBt = giamGia(id, giaBt, dangChay);
            Long giamBt = gBt == null ? null : gBt.giaSauGiam();
            long hienBt = giamBt != null ? giamBt : giaBt;
            String ttBt = (String) bt.get("trangThaiHienThi");
            List<?> anhBt = bt.get("danhSachAnh") instanceof List<?> l ? l : List.of();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", bt.get("id"));
            m.put("ten", bt.get("ten"));
            m.put("macDinh", bt.get("macDinh"));
            m.put("mau", bt.get("mau"));
            m.put("maMau", bt.get("maMau"));
            m.put("tonKho", bt.get("tonKho"));
            m.put("gia", giaBt);
            m.put("giaSauGiam", giamBt);
            m.put("phanTram", gBt == null ? null : phanTram(gBt));
            m.put("giaHien", hienBt);
            m.put("giaHienChu", chuGia(loai, hienBt, giaChu));
            m.put("nhanTrangThai", nhanTrangThai(ttBt));
            // Đặt được = shop đang bán, không phải hàng mẫu, và phân loại này chưa bị đánh dấu hết hàng
            m.put("coTheDat", sanSangBan && !"het_hang".equals(ttBt));
            m.put("danhSachAnh", anhBt);
            m.put("hinhAnh", anhBt.isEmpty() ? null : anhBt.get(0));
            bienThe.add(m);

            giaTu = Math.min(giaTu, hienBt);
            giaDen = Math.max(giaDen, hienBt);
            themAnh(daCoAnh, bt.get("danhSachAnh"));
        }
        // Sản phẩm chưa có biến thể nào (chỉ xảy ra khi sửa tay trên database): khoảng giá
        // chính là giá sản phẩm
        if (bienThe.isEmpty()) {
            giaTu = giaHien;
            giaDen = giaHien;
        }
        List<String> anh = new ArrayList<>(daCoAnh);

        List<Map<String, Object>> boSuuTap = new ArrayList<>();
        for (Object o : sp.get("boSuuTap") instanceof List<?> ds ? ds : List.of()) {
            if (!(o instanceof Map<?, ?> bo)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ten", bo.get("ten"));
            m.put("duongDan", bo.get("duongDan"));
            boSuuTap.add(m);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("ten", sp.get("ten"));
        m.put("moTa", sp.get("moTa"));
        m.put("gia", gia);
        m.put("giaChu", giaChu);
        m.put("hinhAnh", sp.get("hinhAnh"));
        m.put("danhSachAnh", anh);
        m.put("danhMucId", sp.get("danhMucId"));
        m.put("danhMuc", sp.get("danhMuc"));
        m.put("loaiSanPham", loai);
        m.put("trangThai", trangThai);
        m.put("nhanTrangThai", nhanTrangThai(trangThai));
        m.put("tonKho", sp.get("tonKho"));
        m.put("mauSac", sp.get("mauSac"));
        m.put("giaGoc", gia);
        m.put("giaSauGiam", giaSauGiam);
        m.put("phanTram", phanTram);
        m.put("tenKhuyenMai", tenKhuyenMai);
        m.put("giaHien", giaHien);
        m.put("giaHienChu", chuGia(loai, giaHien, giaChu));
        m.put("coTheDat", !"mau".equals(loai));
        // Khoảng giá SAU giảm của các phân loại; bằng nhau hết thì hai số bằng nhau
        m.put("giaTu", giaTu);
        m.put("giaDen", giaDen);
        m.put("giaTuChu", chuGia(loai, giaTu, giaChu));
        m.put("giaDenChu", chuGia(loai, giaDen, giaChu));
        m.put("bienThe", bienThe);
        m.put("boSuuTap", boSuuTap);
        return m;
    }

    /** Khuyến mãi sản phẩm tốt nhất cho một mức giá; không giảm được đồng nào thì null. */
    private KhuyenMaiService.GiaMon giamGia(Long sanPhamId, long gia, List<KhuyenMai> dangChay) {
        if (gia <= 0 || dangChay.isEmpty()) return null;
        KhuyenMaiService.GiaMon g = khuyenMaiService.giaSauGiam(sanPhamId, gia, dangChay);
        return g.coGiam() ? g : null;
    }

    private static Long phanTram(KhuyenMaiService.GiaMon g) {
        return Math.round(g.giamMoiDonVi() * 100.0 / g.giaGoc());
    }

    /** Chữ giá hiển thị: hàng mẫu -> "Hàng mẫu"; có giá -> "19.000₫"; không có giá -> giaChu / "Liên hệ". */
    private static String chuGia(String loai, long gia, String giaChu) {
        if ("mau".equals(loai)) return "Hàng mẫu";
        if (gia > 0) return KhuyenMaiService.tien(gia);
        return giaChu == null || giaChu.isEmpty() ? "Liên hệ" : giaChu;
    }

    private static String nhanTrangThai(String trangThai) {
        return trangThai == null ? "Sẵn hàng" : NHAN_TRANG_THAI.getOrDefault(trangThai, "Sẵn hàng");
    }

    /** Gom ảnh vào bộ đã có, giữ thứ tự gặp trước và bỏ ảnh trùng. */
    private static void themAnh(Set<String> daCo, Object danhSachAnh) {
        if (!(danhSachAnh instanceof List<?> ds)) return;
        for (Object o : ds) {
            if (o == null) continue;
            String u = String.valueOf(o).trim();
            if (!u.isEmpty()) daCo.add(u);
        }
    }

    /** Cùng luật và thứ tự với KhuyenMaiController.giaSanPham, trên bộ nhớ đệm. */
    private List<KhuyenMai> kmSanPhamDangChay() {
        return khuyenMaiService.khuyenMaiSanPhamDangChay(boNho.khuyenMai().danhSach());
    }

    private static <T> List<T> dauDanhSach(List<T> ds, int gioiHan) {
        return gioiHan <= 0 || ds.size() <= gioiHan ? ds : ds.subList(0, gioiHan);
    }

    private static String tenThuong(Map<String, Object> sp) {
        Object t = sp.get("ten");
        return t == null ? "" : String.valueOf(t).toLowerCase(Locale.ROOT);
    }

    private static long so(Object v) { return v instanceof Number n ? n.longValue() : 0L; }

    private ResponseStatusException khongThay() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
    }
}
