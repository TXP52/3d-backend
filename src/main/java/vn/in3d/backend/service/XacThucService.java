package vn.in3d.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.in3d.backend.entity.NguoiDung;
import vn.in3d.backend.repository.NguoiDungRepository;

import vn.in3d.backend.entity.MaOtp;
import vn.in3d.backend.repository.MaOtpRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
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
    private static final int OTP_PHUT = 5;        // mã OTP sống 5 phút
    private static final int OTP_TOI_DA_SAI = 5;  // nhập sai quá 5 lần thì huỷ mã

    private final NguoiDungRepository nguoiDungRepo;
    private final MaOtpRepository maOtpRepo;
    private final EmailService emailService;
    private final BCryptPasswordEncoder maHoa = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final String secret;

    public XacThucService(NguoiDungRepository nguoiDungRepo,
                          MaOtpRepository maOtpRepo,
                          EmailService emailService,
                          @Value("${in3d.auth.secret:doi-secret-nay-khi-len-production}") String secret) {
        this.nguoiDungRepo = nguoiDungRepo;
        this.maOtpRepo = maOtpRepo;
        this.emailService = emailService;
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

    /** Đăng nhập cho KHÁCH website bán hàng: đúng mật khẩu là có token luôn (không cần OTP). */
    public Map<String, Object> dangNhap(String email, String matKhau) {
        NguoiDung nd = kiemTraMatKhau(email, matKhau);
        return Map.of(
                "token", phatToken(nd),
                "id", nd.getId(),
                "hoTen", nd.getHoTen(),
                "email", nd.getEmail(),
                "vaiTro", nd.getVaiTro()
        );
    }

    /**
     * BƯỚC 1 đăng nhập QUẢN TRỊ: kiểm tra email + mật khẩu, bắt buộc vai trò admin,
     * rồi gửi mã OTP 6 số tới email. Không trả token ở bước này.
     */
    @Transactional
    public Map<String, Object> guiOtpDangNhapAdmin(String email, String matKhau) {
        NguoiDung nd = kiemTraMatKhau(email, matKhau);
        if (!"admin".equals(nd.getVaiTro())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tài khoản này không có quyền quản trị. Chỉ tài khoản admin mới đăng nhập được trang quản trị.");
        }

        String ma = String.format("%06d", random.nextInt(1_000_000));
        MaOtp otp = new MaOtp();
        otp.setEmail(nd.getEmail());
        otp.setMa(ma);
        otp.setHetHan(OffsetDateTime.now().plusMinutes(OTP_PHUT));
        maOtpRepo.save(otp);

        boolean daGui = emailService.guiOtp(nd.getEmail(), ma, OTP_PHUT);

        return Map.of(
                "canOtp", true,
                "email", nd.getEmail(),
                "hoTen", nd.getHoTen(),
                "phutHieuLuc", OTP_PHUT,
                "daGuiEmail", daGui,
                "thongBao", daGui
                        ? "Đã gửi mã xác thực tới " + che(nd.getEmail()) + ". Vui lòng kiểm tra hộp thư."
                        : "Chưa cấu hình Gmail nên mã OTP được in ở cửa sổ chạy backend (console)."
        );
    }

    /** BƯỚC 2 đăng nhập QUẢN TRỊ: xác thực mã OTP, đúng thì phát token. */
    @Transactional
    public Map<String, Object> xacThucOtp(String email, String ma) {
        String mail = email == null ? "" : email.trim();
        MaOtp otp = maOtpRepo.findFirstByEmailIgnoreCaseAndDaDungFalseOrderByIdDesc(mail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Chưa có mã xác thực nào. Hãy đăng nhập lại để nhận mã mới."));

        if (otp.getHetHan().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã xác thực đã hết hạn. Hãy bấm gửi lại mã.");
        }
        if (otp.getSoLanSai() >= OTP_TOI_DA_SAI) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn đã nhập sai quá nhiều lần. Hãy đăng nhập lại để nhận mã mới.");
        }
        if (!otp.getMa().equals(ma == null ? "" : ma.trim())) {
            otp.setSoLanSai(otp.getSoLanSai() + 1);
            maOtpRepo.save(otp);
            int conLai = OTP_TOI_DA_SAI - otp.getSoLanSai();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mã xác thực không đúng." + (conLai > 0 ? " Bạn còn " + conLai + " lần thử." : ""));
        }

        otp.setDaDung(true);
        maOtpRepo.save(otp);

        NguoiDung nd = nguoiDungRepo.findByEmailIgnoreCase(mail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));

        return Map.of(
                "token", phatToken(nd),
                "id", nd.getId(),
                "hoTen", nd.getHoTen(),
                "email", nd.getEmail(),
                "vaiTro", nd.getVaiTro()
        );
    }

    private NguoiDung kiemTraMatKhau(String email, String matKhau) {
        NguoiDung nd = nguoiDungRepo.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));
        if (!maHoa.matches(matKhau == null ? "" : matKhau, nd.getMatKhauHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        }
        return nd;
    }

    /** Che bớt email khi hiển thị: abc***@gmail.com */
    private String che(String email) {
        int at = email.indexOf('@');
        if (at <= 3) return email;
        return email.substring(0, 3) + "***" + email.substring(at);
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
