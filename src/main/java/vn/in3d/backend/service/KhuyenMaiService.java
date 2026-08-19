package vn.in3d.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.KhuyenMai;
import vn.in3d.backend.repository.KhuyenMaiRepository;

import java.time.format.DateTimeFormatter;

/**
 * Kiểm tra và áp mã khuyến mãi.
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

    public KhuyenMaiService(KhuyenMaiRepository repo) {
        this.repo = repo;
    }

    /** Kết quả áp mã: mã nào, giảm bao nhiêu, còn phải trả bao nhiêu. */
    public record KetQua(KhuyenMai khuyenMai, long tienGiam, long conLai) {}

    /**
     * Tìm mã và kiểm tra đủ điều kiện chưa.
     * Không hợp lệ thì ném lỗi 400 kèm câu tiếng Việt nói rõ vướng ở đâu.
     */
    @Transactional(readOnly = true)
    public KetQua kiemTra(String ma, long tongTien) {
        if (ma == null || ma.isBlank()) {
            throw loi("Vui lòng nhập mã khuyến mãi.");
        }
        KhuyenMai km = repo.findByMaIgnoreCaseAndDaXoaFalse(ma.trim())
                .orElseThrow(() -> loi("Mã \"" + ma.trim().toUpperCase() + "\" không tồn tại."));

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

        long giam = km.tinhTienGiam(tongTien);
        return new KetQua(km, giam, tongTien - giam);
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
