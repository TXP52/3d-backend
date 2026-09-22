package vn.in3d.backend.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.dto.DonTayRequest;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.service.BoNhoDem;
import vn.in3d.backend.service.ChiPhiMayService;
import vn.in3d.backend.service.DonHangService;
import vn.in3d.backend.service.TongHopQuanTri;
import vn.in3d.backend.service.XacThucService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API khởi tạo trang quản trị — MỘT request cho cả trang, cộng mấy lệnh ghi chỉ trang
 * quản trị mới gọi (đơn gõ tay, danh bạ khách hàng).
 *
 *   GET  /api/quan-tri/khoi-tao?bo=san-pham,tk-san-pham,dem[&tiLeLai=120][&lamMoi=1]
 *   POST /api/quan-tri/lam-moi                  nạp lại toàn bộ bộ nhớ đệm (sau khi sửa tay trên Supabase) — chỉ admin
 *   POST /api/quan-tri/von/gia-mau-in           tính giá các mẫu in chủ shop tự nhập (chỉ tính, không lưu)
 *   PUT  /api/quan-tri/chi-phi-may              sửa định mức chi phí chạy máy in (bảng cai_dat)
 *   POST /api/quan-tri/don-hang                 chủ shop gõ đơn tay (Facebook / Zalo / tại shop) — chỉ admin
 *   POST/PUT/DELETE /api/quan-tri/khach-hang    danh bạ khách hàng — chỉ admin
 *
 * Trước đây mỗi trang gọi đủ 9 API và chờ cái chậm nhất (/san-pham ~2,4 s) rồi tự cộng
 * trừ bằng JS. Giờ trang khai đúng các khoá nó cần; bộ dữ liệu lấy từ bộ nhớ đệm, số liệu
 * tổng hợp tính trong bộ nhớ (TongHopQuanTri) — không hỏi database.
 */
@RestController
@RequestMapping("/api/quan-tri")
public class QuanTriController {

    /** Cùng một câu cho mọi lệnh ghi danh bạ khách hàng. */
    private static final String LOI_KHACH_HANG = "Chỉ admin mới quản lý được khách hàng";

    private final BoNhoDem boNho;
    private final TongHopQuanTri tongHop;
    private final XacThucService xacThuc;
    private final DonHangService donHangService;
    private final ChiPhiMayService chiPhiMay;

    public QuanTriController(BoNhoDem boNho, TongHopQuanTri tongHop, XacThucService xacThuc,
                             DonHangService donHangService, ChiPhiMayService chiPhiMay) {
        this.boNho = boNho;
        this.tongHop = tongHop;
        this.xacThuc = xacThuc;
        this.donHangService = donHangService;
        this.chiPhiMay = chiPhiMay;
    }

    /**
     * Trả object CHỈ gồm các khoá được hỏi (khoá lạ bỏ qua, không có trong kết quả).
     * Khoá bộ dữ liệu có giá trị y hệt API tương ứng (san-pham = GET /api/san-pham?tatCa=true...).
     * chi-phi-may = chi phí chạy máy in mỗi giờ (ChiPhiMayService.thanhMap) — đúng con số đã
     * dùng để ghép tiền máy vào san-pham trong cùng request.
     * nguoi-dung và khach-hang cần header Authorization của admin như GET /api/nguoi-dung;
     * thiếu / sai / không phải admin thì giá trị là null (không báo lỗi cả request).
     * lamMoi=1 cũng chỉ admin mới được, người khác gửi lên thì bỏ qua (xem POST /lam-moi).
     */
    @GetMapping("/khoi-tao")
    public Map<String, Object> khoiTao(@RequestParam(defaultValue = "") String bo,
                                       @RequestParam(required = false) String tiLeLai,
                                       @RequestParam(required = false) String lamMoi,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        Boolean laAdmin = null;   // chỉ đọc token khi có khoá cần
        // Làm mới = 11 truy vấn toàn bảng và làm nguội bộ nhớ đệm của mọi người: CHỈ admin.
        // Không phải admin thì bỏ qua lặng lẽ (request vẫn trả dữ liệu bình thường) —
        // một URL cũ còn dính lamMoi=1, một lượt prefetch hay con bot đều không hại được.
        if ("1".equals(lamMoi) || "true".equalsIgnoreCase(lamMoi)) {
            laAdmin = laAdmin(authorization);
            if (laAdmin) boNho.lamMoiTatCa();
        }

        Map<String, Object> ra = new LinkedHashMap<>();
        // Chi phí máy tính MỘT lần cho cả request: san-pham và chi-phi-may cùng một con số
        ChiPhiMayService.ChiPhiMay[] chiPhi = new ChiPhiMayService.ChiPhiMay[1];
        for (String phan : bo.split(",")) {
            String khoa = phan.trim();
            if (khoa.isEmpty() || ra.containsKey(khoa)) continue;
            switch (khoa) {
                case "san-pham" -> {
                    if (chiPhi[0] == null) chiPhi[0] = chiPhiMay.tinhAnToan();
                    ra.put(khoa, ChiPhiMayService.kemChiPhi(boNho.dsSanPham(true), chiPhi[0]));
                }
                case "chi-phi-may" -> {
                    if (chiPhi[0] == null) chiPhi[0] = chiPhiMay.tinh();
                    ra.put(khoa, ChiPhiMayService.thanhMap(chiPhi[0]));
                }
                case "vat-tu" -> ra.put(khoa, boNho.dsVatTu());
                case "nha-cung-cap" -> ra.put(khoa, boNho.dsNhaCungCap());
                case "mau-sac" -> ra.put(khoa, boNho.dsMauSac());
                case "danh-muc" -> ra.put(khoa, boNho.dsDanhMuc(true, null, false));
                case "bo-suu-tap" -> ra.put(khoa, boNho.dsBoSuuTap(true));
                case "bai-viet" -> ra.put(khoa, boNho.dsBaiViet(true));
                case "khuyen-mai" -> ra.put(khoa, boNho.dsKhuyenMai(true));
                case "don-hang" -> ra.put(khoa, boNho.dsDonHang());
                case "nguoi-dung" -> {
                    if (laAdmin == null) laAdmin = laAdmin(authorization);
                    ra.put(khoa, laAdmin ? boNho.dsNguoiDung() : null);
                }
                case "dem" -> ra.put(khoa, tongHop.dem());
                case "tong-quan" -> ra.put(khoa, tongHop.tongQuan());
                case "bao-cao" -> ra.put(khoa, tongHop.baoCao());
                case "khach-hang" -> {
                    if (laAdmin == null) laAdmin = laAdmin(authorization);
                    ra.put(khoa, laAdmin ? tongHop.khachHang() : null);
                }
                case "von" -> ra.put(khoa, tongHop.von(soThuc(tiLeLai)));
                case "tk-san-pham" -> ra.put(khoa, tongHop.tkSanPham());
                case "tk-kho" -> ra.put(khoa, tongHop.tkKho());
                case "tk-don-hang" -> ra.put(khoa, tongHop.tkDonHang());
                case "tk-khuyen-mai" -> ra.put(khoa, tongHop.tkKhuyenMai());
                case "tk-bai-viet" -> ra.put(khoa, tongHop.tkBaiViet());
                case "tk-nha-cung-cap" -> ra.put(khoa, tongHop.tkNhaCungCap());
                case "tk-mau-sac" -> ra.put(khoa, tongHop.tkMauSac());
                case "tk-danh-muc" -> ra.put(khoa, tongHop.tkDanhMuc());
                case "tk-bo-suu-tap" -> ra.put(khoa, tongHop.tkBoSuuTap());
                case "tk-cai-dat" -> ra.put(khoa, tongHop.tkCaiDat());
                default -> { /* khoá lạ: bỏ qua */ }
            }
        }
        return ra;
    }

    /**
     * Nút "Làm mới": bỏ và nạp lại cả 11 bộ dữ liệu, chờ nạp xong mới trả. Không ghi gì vào database.
     *
     * CHỈ ADMIN: mỗi lượt là 11 truy vấn toàn bảng và trong lúc đó mọi request khác cũng
     * phải chờ database, nên ai cũng gọi được thì chỉ cần một vòng lặp là tắt luôn tác dụng
     * của bộ nhớ đệm và hút hết kết nối của pooler Supabase.
     * Hai lượt bấm sát nhau được BoNhoDem gộp lại (trả thời gian của lượt vừa nạp).
     */
    @PostMapping("/lam-moi")
    public Map<String, Object> lamMoi(@RequestHeader(value = "Authorization", required = false) String authorization) {
        NguoiDung nguoiGoi = xacThuc.docToken(authorization);   // thiếu / sai token -> 401 {loi}
        if (!"admin".equals(nguoiGoi.getVaiTro())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ admin mới làm mới được dữ liệu");
        }
        Map<String, Object> ra = new LinkedHashMap<>();
        ra.put("daLamMoi", true);
        ra.put("thoiGianMs", boNho.lamMoiTatCa());
        return ra;
    }

    /**
     * Giá dự kiến các mẫu in (danh sách chủ shop tự nhập, đang lưu ở trình duyệt).
     * Body: { "tiLeLai": 120, "mauIn": [{ "ten": "Móc khoá", "gram": 12, "phut": 45 }] } — phut
     * (phút in) không bắt buộc, thiếu thì ước theo gram; tiLeLai có thể gửi qua query thay cho body.
     * Giá gồm tiền nhựa + tiền máy (TongHopQuanTri.giaMauIn). CHỈ TÍNH trên bộ nhớ đệm, không lưu gì.
     */
    @PostMapping("/von/gia-mau-in")
    public Map<String, Object> giaMauIn(@RequestBody(required = false) Map<String, Object> body,
                                        @RequestParam(required = false) String tiLeLai) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Double tiLe = b.get("tiLeLai") != null ? soThuc(b.get("tiLeLai")) : soThuc(tiLeLai);
        Object mauIn = b.get("mauIn");
        return tongHop.giaMauIn(mauIn instanceof List<?> ds ? ds : List.of(), tiLe);
    }

    /* ============================================================
       Chi phí chạy máy in
       ============================================================ */

    /**
     * Sửa định mức chi phí máy: {tuoiThoGio?, congSuatW?, giaDienKwh?, baoTriMoiGio?, gramMoiGio?}.
     * Ô không gửi thì để yên, gửi null / rỗng là quay về mặc định, số thì phải dương.
     * Trả đúng object khoá chi-phi-may của khoi-tao. Như các lệnh ghi quản trị khác hiện
     * nay, chưa đòi token (xem hợp đồng v3 mục 2).
     */
    @PutMapping("/chi-phi-may")
    public Map<String, Object> suaChiPhiMay(@RequestBody(required = false) Map<String, Object> body) {
        return chiPhiMay.luu(body);
    }

    /* ============================================================
       Đơn gõ tay
       ============================================================ */

    /**
     * Chủ shop tạo đơn tay: khách mua qua Facebook / Zalo / tại shop.
     * Giá đi đúng đường tính giá của đơn khách tự đặt (mỗi dòng ghi đè được đơn giá),
     * có kênh bán, trạng thái, cách thanh toán, VÀ TRỪ KHO y như đơn web.
     * Trả đơn vừa tạo, đúng dạng một phần tử của GET /api/don-hang.
     */
    @PostMapping("/don-hang")
    @ResponseStatus(HttpStatus.CREATED)
    public DonHang taoDonTay(@Valid @RequestBody DonTayRequest yeuCau,
                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        batBuocAdmin(authorization, "Chỉ admin mới tạo được đơn hàng");
        DonHangService.KetQuaTaoDon kq = donHangService.datHangTay(yeuCau);
        // Đã commit: nạp lại đơn hàng, khuyến mãi, và sản phẩm nếu tồn kho vừa đổi
        boNho.xoaVaNapLai(kq.doiKho()
                ? new String[] {BoNhoDem.DH, BoNhoDem.KM, BoNhoDem.SP}
                : new String[] {BoNhoDem.DH, BoNhoDem.KM});
        return kq.don();
    }

    /* ============================================================
       Danh bạ khách hàng (bảng nguoi_dung)
       ============================================================ */

    /**
     * Thêm khách: {hoTen, soDienThoai, email?, diaChi?, ghiChu?}.
     * Khách này CHƯA có mật khẩu nên chưa đăng nhập được, chỉ để gắn đơn và nhớ liên hệ.
     */
    @PostMapping("/khach-hang")
    @ResponseStatus(HttpStatus.CREATED)
    public NguoiDung themKhachHang(@RequestBody Map<String, Object> td,
                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        batBuocAdmin(authorization, LOI_KHACH_HANG);
        NguoiDung nd = xacThuc.themKhachHang(td);
        return traKhachHang(nd);
    }

    /** Sửa khách; khoá nào không gửi thì để yên. Tài khoản quản trị thì không sửa ở đây. */
    @PutMapping("/khach-hang/{id}")
    public NguoiDung suaKhachHang(@PathVariable Long id, @RequestBody Map<String, Object> td,
                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        batBuocAdmin(authorization, LOI_KHACH_HANG);
        NguoiDung nd = xacThuc.suaKhachHang(id, td);
        return traKhachHang(nd);
    }

    /** Xoá mềm khách (đơn cũ của khách vẫn còn nguyên). */
    @DeleteMapping("/khach-hang/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoaKhachHang(@PathVariable Long id,
                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        batBuocAdmin(authorization, LOI_KHACH_HANG);
        xacThuc.xoaKhachHang(id);
        boNho.xoaVaNapLai(BoNhoDem.ND);
    }

    /**
     * Đã commit: nạp lại danh sách tài khoản rồi trả dòng mới từ bộ nhớ đệm.
     * Nạp lại lỗi thì KHÔNG đọc lại bộ nhớ đệm (đọc là mở thêm một lượt nạp, lỗi lần
     * hai thì lệnh ghi ĐÃ COMMIT lại thành 500 và chủ shop bấm Lưu lần nữa là có hai
     * khách trùng) — trả luôn bản ghi vừa ghi trong transaction.
     */
    private NguoiDung traKhachHang(NguoiDung daGhi) {
        if (boNho.xoaVaNapLai(BoNhoDem.ND)) {
            try {
                for (NguoiDung nd : boNho.dsNguoiDung()) {
                    if (nd.getId().equals(daGhi.getId())) return nd;
                }
            } catch (RuntimeException boQua) {
                // rơi xuống dùng bản vừa ghi
            }
        }
        return daGhi;
    }

    /** Token phải hợp lệ (thiếu / sai -> 401) và phải là admin (-> 403). */
    private void batBuocAdmin(String authorization, String thongBao) {
        NguoiDung nguoiGoi = xacThuc.docToken(authorization);
        if (!"admin".equals(nguoiGoi.getVaiTro())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, thongBao);
        }
    }

    /** "120" / 120 -> 120.0; rỗng hoặc không phải số -> null (TongHopQuanTri tự dùng mặc định 120%). */
    private static Double soThuc(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException boQua) { return null; }
    }

    /** Token hợp lệ và là admin (cùng luật GET /api/nguoi-dung); mọi lỗi đọc token coi như không phải admin. */
    private boolean laAdmin(String authorization) {
        try {
            NguoiDung nd = xacThuc.docToken(authorization);
            return nd != null && "admin".equals(nd.getVaiTro());
        } catch (Exception khongHopLe) {
            return false;
        }
    }
}
