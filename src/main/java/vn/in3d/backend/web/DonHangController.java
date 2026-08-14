package vn.in3d.backend.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.in3d.backend.dto.DatHangRequest;
import vn.in3d.backend.entity.DonHang;
import vn.in3d.backend.service.DonHangService;

import java.util.List;
import java.util.Map;

/** API đơn hàng: website bán hàng tạo đơn, trang quản trị xem/cập nhật. */
@RestController
@RequestMapping("/api/don-hang")
public class DonHangController {

    private final DonHangService donHangService;

    public DonHangController(DonHangService donHangService) {
        this.donHangService = donHangService;
    }

    /** Khách đặt hàng (website bán hàng gọi). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DonHang datHang(@Valid @RequestBody DatHangRequest yeuCau) {
        return donHangService.datHang(yeuCau);
    }

    /** Danh sách đơn, mới nhất trước (trang quản trị gọi). */
    @GetMapping
    public List<DonHang> danhSach() {
        return donHangService.danhSachDon();
    }

    /** Đổi trạng thái đơn: {"trangThai": "dang_giao"} */
    @PutMapping("/{id}/trang-thai")
    public DonHang doiTrangThai(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return donHangService.doiTrangThai(id, body.getOrDefault("trangThai", ""));
    }

    /** Đánh dấu đơn đã thanh toán. */
    @PutMapping("/{id}/da-thanh-toan")
    public DonHang daThanhToan(@PathVariable Long id) {
        return donHangService.danhDauDaThanhToan(id);
    }

    /** Xoá đơn. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable Long id) {
        donHangService.xoaDon(id);
    }
}
