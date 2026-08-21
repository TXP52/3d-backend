package vn.in3d.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.repository.DonHangRepository;
import vn.in3d.backend.repository.KhuyenMaiRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;

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
 * Dùng chung cho hai chỗ:
 *   - /api/khuyen-mai/kiem-tra : khách bấm "Áp dụng" ở giỏ hàng để xem trước
 *   - DonHangService.datHang   : lúc tạo đơn thật, tính lại từ đầu
 *
 * Tính lại ở bước tạo đơn là bắt buộc — số tiền giảm trình duyệt gửi lên
 * hoàn toàn có thể bị sửa, không được tin.
 */
@Service
public class KhuyenMaiService {

    private static final DateTimeFormatter NGAY_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final KhuyenMaiRepository repo;
    private final DonHangRepository donHangRepo;

    public KhuyenMaiService(KhuyenMaiRepository repo, DonHangRepository donHangRepo) {
        this.repo = repo;
        this.donHangRepo = donHangRepo;
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

    /* ============================================================
       GIẢM GIÁ SẢN PHẨM — tự áp, không cần mã
       ============================================================ */

    /** Các chương trình giảm giá sản phẩm đang chạy. */
    @Transactional(readOnly = true)
    public List<KhuyenMai> khuyenMaiSanPhamDangChay() {
        return repo.findByDaXoaFalseOrderByIdDesc().stream()
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

    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public KetQua kiemTra(String ma, long tongTien) {
        return kiemTra(ma, tongTien, NguoiDat.khongRo());
    }

    @Transactional(readOnly = true)
    public KetQua kiemTra(String ma, long tongTien, NguoiDat nguoiDat) {
        if (ma == null || ma.isBlank()) {
            throw loi("Vui lòng nhập mã khuyến mãi.");
        }
        KhuyenMai km = repo.findByMaIgnoreCaseAndDaXoaFalse(ma.trim())
                .orElseThrow(() -> loi("Mã \"" + ma.trim().toUpperCase() + "\" không tồn tại."));

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

    /** Ghi nhận đã dùng thêm 1 lượt. Gọi sau khi đơn được lưu thành công. */
    @Transactional
    public void ghiNhanDaDung(Long id) {
        repo.findById(id).ifPresent(km -> {
            km.setDaDung(km.getDaDung() + 1);
            repo.save(km);
        });
    }

    private ResponseStatusException loi(String thongBao) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, thongBao);
    }

    /** 50000 -> "50.000₫" */
    static String tien(long so) {
        return String.format("%,d", so).replace(',', '.') + "₫";
    }
}
