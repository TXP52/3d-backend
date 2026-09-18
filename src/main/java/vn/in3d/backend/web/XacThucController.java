package vn.in3d.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.service.BoNhoDem;
import vn.in3d.backend.service.XacThucService;

import java.util.List;
import java.util.Map;

/**
 * API tài khoản: đăng ký / đăng nhập / thông tin phiên — lưu trong database,
 * KHÔNG dùng Supabase Auth. Lỗi trả về dạng {"loi": "..."} tiếng Việt.
 */
@RestController
@RequestMapping("/api")
public class XacThucController {

    private final XacThucService xacThuc;
    private final BoNhoDem boNho;

    public XacThucController(XacThucService xacThuc, BoNhoDem boNho) {
        this.xacThuc = xacThuc;
        this.boNho = boNho;
    }

    /** Đăng ký tài khoản. Người đầu tiên đăng ký tự động là admin. */
    @PostMapping("/auth/dang-ky")
    @ResponseStatus(HttpStatus.CREATED)
    public NguoiDung dangKy(@RequestBody Map<String, String> body) {
        NguoiDung nd = xacThuc.dangKy(body.get("hoTen"), body.get("email"), body.get("matKhau"));
        // dangKy đã commit: nạp lại danh sách tài khoản (token của người mới đọc được ngay từ bộ nhớ đệm)
        boNho.xoaVaNapLai(BoNhoDem.ND);
        return nd;
    }

    /** Đăng nhập cho KHÁCH (website bán hàng): trả token luôn, không cần OTP. */
    @PostMapping("/auth/dang-nhap")
    public Map<String, Object> dangNhap(@RequestBody Map<String, String> body) {
        return xacThuc.dangNhap(body.get("email"), body.get("matKhau"));
    }

    /** BƯỚC 1 đăng nhập QUẢN TRỊ: đúng email + mật khẩu + là admin -> gửi OTP qua email. */
    @PostMapping("/auth/admin/dang-nhap")
    public Map<String, Object> dangNhapAdmin(@RequestBody Map<String, String> body) {
        return xacThuc.guiOtpDangNhapAdmin(body.get("email"), body.get("matKhau"));
    }

    /** BƯỚC 2 đăng nhập QUẢN TRỊ: nhập đúng mã OTP -> nhận token. */
    @PostMapping("/auth/admin/xac-thuc-otp")
    public Map<String, Object> xacThucOtp(@RequestBody Map<String, String> body) {
        return xacThuc.xacThucOtp(body.get("email"), body.get("ma"));
    }

    /** Thông tin người dùng của token hiện tại (header: Authorization: Bearer <token>). */
    @GetMapping("/auth/toi")
    public NguoiDung toi(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return xacThuc.docToken(authorization);
    }

    /** Danh sách người dùng — chỉ admin (kèm token) xem được. */
    @GetMapping("/nguoi-dung")
    public List<NguoiDung> danhSach(@RequestHeader(value = "Authorization", required = false) String authorization) {
        NguoiDung nguoiGoi = xacThuc.docToken(authorization);
        if (!"admin".equals(nguoiGoi.getVaiTro())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ admin mới xem được danh sách người dùng");
        }
        return xacThuc.danhSachNguoiDung();
    }
}
