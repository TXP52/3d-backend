package vn.in3d.backend.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.in3d.backend.dto.DatHangRequest;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.service.BoNhoDem;
import vn.in3d.backend.service.DonHangService;
import vn.in3d.backend.service.NapDuLieu;
import vn.in3d.backend.service.XacThucService;

import java.util.List;
import java.util.Map;

/** API đơn hàng: website bán hàng tạo đơn, trang quản trị xem/cập nhật. */
@RestController
@RequestMapping("/api/don-hang")
public class DonHangController {

    private final DonHangService donHangService;
    private final XacThucService xacThuc;
    private final BoNhoDem boNho;
    private final NapDuLieu napDuLieu;

    public DonHangController(DonHangService donHangService, XacThucService xacThuc,
                             BoNhoDem boNho, NapDuLieu napDuLieu) {
        this.donHangService = donHangService;
        this.xacThuc = xacThuc;
        this.boNho = boNho;
        this.napDuLieu = napDuLieu;
    }

    /**
     * Khách đặt hàng (website bán hàng gọi).
     * Có token thì nối đơn vào tài khoản; không có vẫn đặt được như khách vãng lai.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DonHang datHang(@Valid @RequestBody DatHangRequest yeuCau,
                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        NguoiDung nd = xacThuc.docTokenNeuCo(authorization);
        DonHangService.KetQuaTaoDon kq = donHangService.datHang(yeuCau, nd == null ? null : nd.getId());
        // Đã commit (đơn mới + có thể tăng lượt dùng mã + trừ kho biến thể):
        // nạp lại đơn hàng, khuyến mãi, và sản phẩm nếu tồn kho vừa đổi
        boNho.xoaVaNapLai(khoaCanXoa(kq.doiKho(), BoNhoDem.DH, BoNhoDem.KM));
        return kq.don();
    }

    /** Danh sách đơn, mới nhất trước (trang quản trị gọi) — từ bộ nhớ đệm. */
    @GetMapping
    public List<DonHang> danhSach() {
        return boNho.dsDonHang();
    }

    /**
     * Đổi trạng thái đơn: {"trangThai": "dang_giao"}. Trả đơn đúng dạng trong danh sách.
     * Chuyển VÀO "đã huỷ" là trả hàng về kho, chuyển RA khỏi "đã huỷ" là trừ kho lại.
     */
    @PutMapping("/{id}/trang-thai")
    public DonHang doiTrangThai(@PathVariable Long id, @RequestBody Map<String, String> body) {
        boolean doiKho = donHangService.doiTrangThai(id, body.getOrDefault("trangThai", ""));
        return traDon(id, doiKho);
    }

    /** Đánh dấu đơn đã thanh toán. Trả đơn đúng dạng trong danh sách. */
    @PutMapping("/{id}/da-thanh-toan")
    public DonHang daThanhToan(@PathVariable Long id) {
        donHangService.danhDauDaThanhToan(id);
        return traDon(id, false);
    }

    /** Xoá đơn (xoá mềm). Đơn đang giữ hàng thì trả hàng về kho. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        boolean doiKho = donHangService.xoaDon(id);
        boNho.xoaVaNapLai(khoaCanXoa(doiKho, BoNhoDem.DH));
    }

    /**
     * Đã ghi: nạp lại đơn hàng rồi trả đơn từ bộ nhớ đệm (đơn đã xoá mềm không nằm trong đó thì đọc riêng).
     *
     * Nạp lại lỗi (Supabase chớp một nhịp) thì KHÔNG đọc lại bộ nhớ đệm nữa — đọc là mở thêm
     * một lượt nạp, lỗi lần hai thì ném ra ngoài và lệnh ghi ĐÃ COMMIT lại thành lỗi 500.
     * Đọc thẳng một dòng (một lượt đi-về); không được nữa thì trả rỗng, trang quản trị hỏi
     * lại danh sách đơn ngay sau đó.
     *
     * @param doiKho lượt ghi vừa rồi có đổi tồn kho biến thể không
     */
    private DonHang traDon(Long id, boolean doiKho) {
        if (boNho.xoaVaNapLai(khoaCanXoa(doiKho, BoNhoDem.DH))) {
            try {
                DonHang don = boNho.donHang().theoId().get(id);
                if (don != null) return don;
            } catch (RuntimeException boQua) {
                // rơi xuống đọc thẳng một dòng
            }
        }
        try {
            return napDuLieu.motDonHang(id);
        } catch (RuntimeException boQua) {
            return null;
        }
    }

    /** Kho vừa đổi thì phải nạp lại cả sản phẩm (tồn kho nằm trong DTO sản phẩm). */
    private static String[] khoaCanXoa(boolean doiKho, String... khoa) {
        if (!doiKho) return khoa;
        String[] ra = new String[khoa.length + 1];
        System.arraycopy(khoa, 0, ra, 0, khoa.length);
        ra[khoa.length] = BoNhoDem.SP;
        return ra;
    }
}
