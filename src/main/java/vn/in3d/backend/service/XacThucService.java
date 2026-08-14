package vn.in3d.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.repository.NguoiDungRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Xác thực người dùng: đăng ký (BCrypt), đăng nhập (phát token HMAC), đọc token.
 * Token dạng: base64url(email|vaiTro|hạn) + "." + base64url(HMAC-SHA256(payload, secret))
 */
@Service
public class XacThucService {

    private static final long HAN_TOKEN_MS = 7L * 24 * 60 * 60 * 1000; // 7 ngày

    private final NguoiDungRepository nguoiDungRepo;
    private final BCryptPasswordEncoder maHoa = new BCryptPasswordEncoder();
    private final String secret;

    public XacThucService(NguoiDungRepository nguoiDungRepo,
                          @Value("${in3d.auth.secret:doi-secret-nay-khi-len-production}") String secret) {
        this.nguoiDungRepo = nguoiDungRepo;
        this.secret = secret;
    }

    /** Đăng ký. Người dùng ĐẦU TIÊN của hệ thống tự động là admin. */
    @Transactional
    public NguoiDung dangKy(String hoTen, String email, String matKhau) {
        if (hoTen == null || hoTen.isBlank()) loi400("Vui lòng nhập họ và tên");
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) loi400("Email không hợp lệ");
        if (matKhau == null || matKhau.length() < 6) loi400("Mật khẩu phải có ít nhất 6 ký tự");
        if (nguoiDungRepo.existsByEmailIgnoreCase(email.trim())) loi400("Email này đã được đăng ký. Hãy đăng nhập.");

        NguoiDung nd = new NguoiDung();
        nd.setHoTen(hoTen.trim());
        nd.setEmail(email.trim().toLowerCase());
        nd.setMatKhauHash(maHoa.encode(matKhau));
        nd.setVaiTro(nguoiDungRepo.count() == 0 ? "admin" : "khach_hang");
        return nguoiDungRepo.save(nd);
    }

    /** Đăng nhập, trả về token + thông tin người dùng. */
    public Map<String, Object> dangNhap(String email, String matKhau) {
        NguoiDung nd = nguoiDungRepo.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));
        if (!maHoa.matches(matKhau == null ? "" : matKhau, nd.getMatKhauHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        }
        return Map.of(
                "token", phatToken(nd),
                "id", nd.getId(),
                "hoTen", nd.getHoTen(),
                "email", nd.getEmail(),
                "vaiTro", nd.getVaiTro()
        );
    }

    /** Đọc token từ header Authorization, trả về người dùng (hoặc 401). */
    public NguoiDung docToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) loi401();
        String token = authorization.substring(7).trim();
        String[] phan = token.split("\\.");
        if (phan.length != 2) loi401();
        String payload = new String(Base64.getUrlDecoder().decode(phan[0]), StandardCharsets.UTF_8);
        if (!hmac(phan[0]).equals(phan[1])) loi401();

        String[] truong = payload.split("\\|");
        if (truong.length != 3) loi401();
        if (Long.parseLong(truong[2]) < System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn, hãy đăng nhập lại");
        }
        Optional<NguoiDung> nd = nguoiDungRepo.findByEmailIgnoreCase(truong[0]);
        if (nd.isEmpty()) loi401();
        return nd.get();
    }

    public List<NguoiDung> danhSachNguoiDung() {
        return nguoiDungRepo.findAll();
    }

    private String phatToken(NguoiDung nd) {
        String payload = nd.getEmail() + "|" + nd.getVaiTro() + "|" + (System.currentTimeMillis() + HAN_TOKEN_MS);
        String p64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return p64 + "." + hmac(p64);
    }

    private String hmac(String duLieu) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(duLieu.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi tạo chữ ký token", e);
        }
    }

    private void loi400(String thongBao) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, thongBao);
    }

    private void loi401() {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token không hợp lệ");
    }
}
