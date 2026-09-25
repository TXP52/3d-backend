package vn.in3d.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.repository.DonHangRepository;
import vn.in3d.backend.repository.KhuyenMaiRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Kiểm tra và áp khuyến mãi. Hai kiểu chạy song song:
 *
 *   GIẢM GIÁ SẢN PHẨM (kieu_ap_dung = san_pham)
 *     Tự áp vào giá từng món, khách không phải gõ gì.
 *     Món nằm trong nhiều chương trình thì lấy chương trình giảm NHIỀU NHẤT.
 *
 *   GIẢM GIÁ THEO ĐƠN (kieu_ap_dung = don_hang)
 *     Khách gõ mã ở giỏ hàng, giảm trên tổng đơn SAU khi đã giảm giá món.
 *
 * Dùng chung cho ba chỗ:
 *   - /api/khuyen-mai/kiem-tra  : khách bấm "Áp dụng" ở giỏ hàng để xem trước
 *   - /api/gio-hang/bao-gia     : giỏ hàng hỏi giá cả giỏ
 *   - DonHangService.datHang    : lúc tạo đơn thật, tính lại từ đầu
 *
 * Tính lại ở bước tạo đơn là bắt buộc — số tiền giảm trình duyệt gửi lên
 * hoàn toàn có thể bị sửa, không được tin.
 *
 * Không còn @Transactional(readOnly = true): transaction chỉ-đọc quanh MỘT câu
 * SELECT tốn thêm một lượt COMMIT (~250 ms). Gọi từ datHang thì vẫn nằm trong
 * transaction của đơn; gọi riêng thì câu SELECT tự chạy autocommit.
 */
@Service
public class KhuyenMaiService {

    private static final DateTimeFormatter NGAY_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final KhuyenMaiRepository repo;
    private final DonHangRepository donHangRepo;
    private final JdbcTemplate jdbc;

    public KhuyenMaiService(KhuyenMaiRepository repo, DonHangRepository donHangRepo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.donHangRepo = donHangRepo;
        this.jdbc = jdbc;
    }

    /** Kết quả áp mã đơn hàng: mã nào, giảm bao nhiêu, còn phải trả bao nhiêu. */
    public record KetQua(KhuyenMai khuyenMai, long tienGiam, long conLai) {}

    /**
     * Người đang đặt hàng, để kiểm tra điều kiện "chỉ khách hàng mới"
     * và "địa chỉ phải thuộc khu vực nào".
     */
    public record NguoiDat(Long nguoiDungId, String soDienThoai, String diaChi) {
        public static NguoiDat khongRo() { return new NguoiDat(null, null, null); }
    }

    /** Giá một món sau khi trừ khuyến mãi sản phẩm. */
    public record GiaMon(long giaGoc, long giaSauGiam, long giamMoiDonVi, KhuyenMai khuyenMai) {
        public boolean coGiam() { return giamMoiDonVi > 0; }
    }

    /** Mọi khuyến mãi CHƯA xoá, mới trước — MỘT câu SELECT, nạp một lần rồi dùng cho cả đơn. */
    public List<KhuyenMai> dsChuaXoa() {
        return repo.findByDaXoaFalseOrderByIdDesc();
    }

    /* ============================================================
       GIẢM GIÁ SẢN PHẨM — tự áp, không cần mã
       ============================================================ */

    /** Các chương trình giảm giá sản phẩm đang chạy (hỏi database). */
    public List<KhuyenMai> khuyenMaiSanPhamDangChay() {
        return khuyenMaiSanPhamDangChay(dsChuaXoa());
    }

    /** Lọc chương trình giảm giá sản phẩm đang chạy từ danh sách đã nạp (giữ thứ tự mới trước). */
    public List<KhuyenMai> khuyenMaiSanPhamDangChay(List<KhuyenMai> daNap) {
        return daNap.stream()
                .filter(KhuyenMai::laKhuyenMaiSanPham)
                .filter(KhuyenMai::dangChay)
                .toList();
    }

    /**
     * Giá một món sau giảm. Món thuộc nhiều chương trình thì chọn cái lợi nhất
     * cho khách — không cộng dồn nhiều chương trình sản phẩm vào một món,
     * cộng dồn dễ ra giá 0đ mà chủ shop không lường trước.
     */
    public GiaMon giaSauGiam(Long sanPhamId, long giaGoc, List<KhuyenMai> dangChay) {
        long giamTotNhat = 0;
        KhuyenMai chon = null;
        for (KhuyenMai km : dangChay) {
            if (!km.apDungChoSanPham(sanPhamId)) continue;
            long giam = km.tinhTienGiam(giaGoc);
            if (giam > giamTotNhat) {
                giamTotNhat = giam;
                chon = km;
            }
        }
        return new GiaMon(giaGoc, giaGoc - giamTotNhat, giamTotNhat, chon);
    }

    public GiaMon giaSauGiam(Long sanPhamId, long giaGoc) {
        return giaSauGiam(sanPhamId, giaGoc, khuyenMaiSanPhamDangChay());
    }

    /* ============================================================
       GIẢM GIÁ THEO ĐƠN — khách gõ mã
       ============================================================ */

    /**
     * Tìm mã và kiểm tra đủ điều kiện chưa.
     * Không hợp lệ thì ném lỗi 400 kèm câu tiếng Việt nói rõ vướng ở đâu.
     *
     * @param tongTien tiền hàng ĐÃ trừ khuyến mãi sản phẩm
     */
    public KetQua kiemTra(String ma, long tongTien) {
        return kiemTra(ma, tongTien, NguoiDat.khongRo());
    }

    /** Tra mã trong database (một câu SELECT) rồi kiểm tra. */
    public KetQua kiemTra(String ma, long tongTien, NguoiDat nguoiDat) {
        if (ma == null || ma.isBlank()) {
            throw loi("Vui lòng nhập mã khuyến mãi.");
        }
        return kiemTraMa(repo.findByMaIgnoreCaseAndDaXoaFalse(ma.trim()).orElse(null), ma, tongTien, nguoiDat);
    }

    /**
     * Như trên nhưng tra mã trong danh sách khuyến mãi CHƯA xoá đã nạp sẵn
     * (datHang nạp một lần; giỏ hàng dùng bộ nhớ đệm) — không tốn thêm lượt hỏi database.
     */
    public KetQua kiemTra(String ma, long tongTien, NguoiDat nguoiDat, List<KhuyenMai> daNap) {
        if (ma == null || ma.isBlank()) {
            throw loi("Vui lòng nhập mã khuyến mãi.");
        }
        String tim = ma.trim();
        KhuyenMai km = null;
        for (KhuyenMai k : daNap) {
            if (k.getMa() != null && !k.getDaXoa() && k.getMa().equalsIgnoreCase(tim)) {
                km = k;
                break;
            }
        }
        return kiemTraMa(km, ma, tongTien, nguoiDat);
    }

    private KetQua kiemTraMa(KhuyenMai km, String ma, long tongTien, NguoiDat nguoiDat) {
        if (km == null) {
            throw loi("Mã \"" + ma.trim().toUpperCase() + "\" không tồn tại.");
        }

        // Chương trình giảm giá sản phẩm không phải mã để gõ — nó tự áp vào giá món
        if (km.laKhuyenMaiSanPham()) {
            throw loi("\"" + km.getMa() + "\" là chương trình giảm giá sản phẩm, đã tự trừ vào giá món rồi. "
                    + "Bạn không cần nhập mã này.");
        }

        switch (km.getTrangThai()) {
            case "tam_dung" -> throw loi("Mã này đang tạm dừng.");
            case "het_luot" -> throw loi("Mã này đã hết lượt sử dụng.");
            case "sap_dien_ra" -> throw loi("Mã này bắt đầu từ ngày " + km.getBatDau().format(NGAY_VN) + ".");
            case "het_han" -> throw loi("Mã này đã hết hạn ngày " + km.getKetThuc().format(NGAY_VN) + ".");
            default -> { /* dang_chay: đi tiếp */ }
        }

        if (km.getDonToiThieu() > 0 && tongTien < km.getDonToiThieu()) {
            throw loi("Đơn phải từ " + tien(km.getDonToiThieu()) + " mới dùng được mã này. "
                    + "Đơn hiện tại " + tien(tongTien) + ", còn thiếu " + tien(km.getDonToiThieu() - tongTien) + ".");
        }

        // Chỉ dành cho khách hàng mới
        if (km.getChiKhachMoi() && daTungMuaHang(nguoiDat)) {
            throw loi("Mã này chỉ dành cho khách hàng mới — bạn đã từng đặt hàng ở shop rồi.");
        }

        // Giới hạn khu vực giao hàng (mã freeship nội thành chẳng hạn)
        if (!km.hopDiaChi(nguoiDat.diaChi())) {
            throw loi(nguoiDat.diaChi() == null || nguoiDat.diaChi().isBlank()
                    ? "Mã này chỉ áp dụng cho một số khu vực. Bạn nhập địa chỉ nhận hàng trước rồi áp mã lại nhé."
                    : "Mã này không áp dụng cho địa chỉ bạn nhập. Chỉ giao trong khu vực: "
                      + km.getDieuKienDiaChi().replace(",", ", ") + ".");
        }

        long giam = km.tinhTienGiam(tongTien);
        return new KetQua(km, giam, tongTien - giam);
    }

    /**
     * Khách này đã từng đặt hàng chưa.
     * Ưu tiên tài khoản đăng nhập; chưa đăng nhập thì đối chiếu số điện thoại —
     * không chặt bằng nhưng đủ để không ai lấy mã khách mới dùng mãi.
     * Không biết gì về khách thì coi như khách mới, thà rộng còn hơn chặn oan.
     */
    private boolean daTungMuaHang(NguoiDat n) {
        if (n.nguoiDungId() != null) return donHangRepo.countByNguoiDungId(n.nguoiDungId()) > 0;
        if (n.soDienThoai() != null && !n.soDienThoai().isBlank()) {
            return donHangRepo.countBySoDienThoai(n.soDienThoai().trim()) > 0;
        }
        return false;
    }

    /**
     * Ghi nhận đã dùng thêm 1 lượt. Gọi sau khi đơn được lưu, TRONG transaction của đơn.
     *
     * MỘT câu UPDATE có điều kiện thay cho đọc-sửa-ghi: hai đơn cùng lúc dùng lượt cuối
     * thì database chỉ cho một đơn tăng được. Không tăng được (0 dòng) nghĩa là mã vừa
     * hết lượt — ném 400 để cả đơn rollback, khách không được giảm quá số lượt.
     */
    public void ghiNhanDaDung(Long id) {
        int soDong = jdbc.update("update khuyen_mai set da_dung = da_dung + 1, updated_at = now() "
                + "where id = ? and (so_luong = 0 or da_dung < so_luong)", id);
        if (soDong == 0) throw loi("Mã này đã hết lượt sử dụng.");
    }

    /**
     * Trả lại MỘT lượt cho mã (đơn đổi sang mã khác lúc sửa). Không tìm thấy mã hoặc
     * mã đang ở 0 lượt thì bỏ qua — trả lượt là dọn dẹp, không đáng để hỏng lệnh sửa đơn.
     */
    public void traLaiLuotTheoMa(String ma) {
        if (ma == null || ma.isBlank()) return;
        jdbc.update("update khuyen_mai set da_dung = greatest(0, da_dung - 1), updated_at = now() "
                + "where upper(ma) = upper(?)", ma.trim());
    }

    private ResponseStatusException loi(String thongBao) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, thongBao);
    }

    /** 50000 -> "50.000₫" (cố định dấu chấm, không phụ thuộc ngôn ngữ của máy chạy backend) */
    public static String tien(long so) {
        return String.format(Locale.US, "%,d", so).replace(',', '.') + "₫";
    }
}
