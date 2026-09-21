package vn.in3d.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Xác thực người dùng: đăng ký (BCrypt), đăng nhập (phát token HMAC), đọc token,
 * và DANH BẠ KHÁCH HÀNG chủ shop tự nhập ở trang quản trị (cùng bảng nguoi_dung,
 * chỉ khác là chưa có mật khẩu nên chưa đăng nhập được).
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
    private final BoNhoDem boNho;
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder maHoa = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final String secret;
    /** Email DUY NHẤT được vào trang quản trị. */
    private final String adminEmail;

    public XacThucService(NguoiDungRepository nguoiDungRepo,
                          MaOtpRepository maOtpRepo,
                          EmailService emailService,
                          BoNhoDem boNho,
                          JdbcTemplate jdbc,
                          @Value("${in3d.auth.secret:doi-secret-nay-khi-len-production}") String secret,
                          @Value("${in3d.admin.email:txp5201aquarius@gmail.com}") String adminEmail) {
        this.nguoiDungRepo = nguoiDungRepo;
        this.maOtpRepo = maOtpRepo;
        this.emailService = emailService;
        this.boNho = boNho;
        this.jdbc = jdbc;
        this.secret = secret;
        this.adminEmail = adminEmail.trim().toLowerCase();
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
        // Chặn TRƯỚC khi kiểm tra mật khẩu: email khác thì không cần dò mật khẩu làm gì.
        kiemTraLaAdminDuyNhat(email);
        NguoiDung nd = kiemTraMatKhau(email, matKhau);
        kiemTraLaAdminDuyNhat(nd.getEmail());
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

    /**
     * Chỉ một email duy nhất được vào trang quản trị.
     * Trước đây bất kỳ tài khoản nào mang vai trò admin cũng vào được — mà người
     * ĐẦU TIÊN đăng ký ở website bán hàng lại tự động thành admin.
     */
    private void kiemTraLaAdminDuyNhat(String email) {
        String mail = email == null ? "" : email.trim();
        if (!adminEmail.equalsIgnoreCase(mail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Email này không được phép vào trang quản trị. Hệ thống chỉ mở cho đúng một tài khoản quản trị.");
        }
    }

    /** BƯỚC 2 đăng nhập QUẢN TRỊ: xác thực mã OTP, đúng thì phát token. */
    @Transactional
    public Map<String, Object> xacThucOtp(String email, String ma) {
        String mail = email == null ? "" : email.trim();
        kiemTraLaAdminDuyNhat(mail);
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

    /**
     * Đọc token nhưng KHÔNG ném lỗi khi thiếu / sai / hết hạn — trả null.
     * Dùng ở chỗ đăng nhập là tuỳ chọn: khách vãng lai vẫn đặt hàng được,
     * đăng nhập rồi thì đơn mới nối được vào tài khoản.
     */
    public NguoiDung docTokenNeuCo(String authorization) {
        try { return docToken(authorization); }
        catch (Exception bo) { return null; }
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
        // Tra tài khoản trong bộ nhớ đệm (mọi request có token khỏi một lượt hỏi database);
        // không thấy mới hỏi database — phòng tài khoản vừa thêm tay trên Supabase
        NguoiDung daBiet = boNho.nguoiDungTheoEmail(truong[0]);
        if (daBiet != null) return daBiet;
        Optional<NguoiDung> nd = nguoiDungRepo.findByEmailIgnoreCase(truong[0]);
        if (nd.isEmpty()) loi401();
        return nd.get();
    }

    /** Danh sách tài khoản chưa xoá (từ bộ nhớ đệm). */
    public List<NguoiDung> danhSachNguoiDung() {
        return boNho.dsNguoiDung();
    }

    /* ============================================================
       KHÁCH HÀNG chủ shop tự nhập ở trang quản trị
       ============================================================ */

    /**
     * Thêm khách vào danh bạ: một dòng nguoi_dung vai_tro = khach_hang, KHÔNG có mật
     * khẩu nên chưa đăng nhập được — chỉ để nhớ số điện thoại / địa chỉ và gắn đơn.
     * Email không bắt buộc (khách Facebook / Zalo thường không cho); có thì phải
     * đúng dạng và chưa ai dùng, để trống thì lưu NULL (nhiều dòng NULL vẫn hợp lệ).
     */
    @Transactional
    public NguoiDung themKhachHang(Map<String, Object> td) {
        NguoiDung nd = new NguoiDung();
        nd.setHoTen(hoTenHopLe(chuoi(td.get("hoTen"))));
        // Số điện thoại là thứ DUY NHẤT để nhận ra khách nhập tay (không có email, không
        // đăng nhập) và để gắn đơn gõ tay / đơn cũ vào đúng người — hợp đồng mục 5 bắt buộc
        String soDienThoai = trongThanhNull(chuoi(td.get("soDienThoai")));
        if (soDienThoai == null) loi400("Vui lòng nhập số điện thoại.");
        nd.setSoDienThoai(soDienThoai);
        nd.setEmail(emailHopLe(chuoi(td.get("email")), null));
        nd.setDiaChi(trongThanhNull(chuoi(td.get("diaChi"))));
        nd.setGhiChu(trongThanhNull(chuoi(td.get("ghiChu"))));
        nd.setVaiTro("khach_hang");
        return nguoiDungRepo.save(nd);
    }

    /**
     * Sửa khách: khoá nào không gửi thì để yên. Tài khoản quản trị thì không đụng vào.
     * Khách TỰ ĐĂNG KÝ (có mật khẩu) thì email là tên đăng nhập của họ: gửi lại đúng email
     * cũ thì được, xoá trống hay đổi sang email khác là 400 — không thì khách bị khoá ngoài.
     */
    @Transactional
    public NguoiDung suaKhachHang(Long id, Map<String, Object> td) {
        NguoiDung nd = nguoiDungRepo.findById(id).orElseThrow(this::khongThayKhach);
        if (nd.getDaXoa()) throw khongThayKhach();
        kiemTraKhongPhaiAdmin(nd);
        if (td.containsKey("hoTen")) nd.setHoTen(hoTenHopLe(chuoi(td.get("hoTen"))));
        if (td.containsKey("soDienThoai")) nd.setSoDienThoai(trongThanhNull(chuoi(td.get("soDienThoai"))));
        if (td.containsKey("email") && trongThanhNull(nd.getMatKhauHash()) != null) {
            String gui = trongThanhNull(chuoi(td.get("email")));
            String dangCo = trongThanhNull(nd.getEmail());
            boolean giuNguyen = gui == null ? dangCo == null : gui.equalsIgnoreCase(dangCo);
            if (!giuNguyen) {
                loi400("Khách này đã tự đăng ký tài khoản, email là tên đăng nhập của khách "
                        + "nên không xoá hay đổi được ở đây.");
            }
            // Gửi lại đúng email đang có: để nguyên, khỏi hỏi database xem có trùng không
        } else if (td.containsKey("email")) {
            nd.setEmail(emailHopLe(chuoi(td.get("email")), id));
        }
        if (td.containsKey("diaChi")) nd.setDiaChi(trongThanhNull(chuoi(td.get("diaChi"))));
        if (td.containsKey("ghiChu")) nd.setGhiChu(trongThanhNull(chuoi(td.get("ghiChu"))));
        // nd đang được quản lý trong transaction: commit tự ghi phần thay đổi
        return nd;
    }

    /**
     * XOÁ MỀM khách. Điều kiện vai_tro nằm luôn trong câu UPDATE: bộ nhớ đệm có cũ
     * một nhịp thì tài khoản quản trị vẫn không xoá được.
     */
    public void xoaKhachHang(Long id) {
        NguoiDung nd = timKhach(id);
        kiemTraKhongPhaiAdmin(nd);
        if (jdbc.update("update nguoi_dung set is_deleted = true, updated_at = now() "
                + "where id = ? and vai_tro <> 'admin'", id) == 0) {
            throw khongThayKhach();
        }
    }

    /** Khách chưa xoá trong bộ nhớ đệm; không có thì 404. */
    private NguoiDung timKhach(Long id) {
        for (NguoiDung nd : boNho.dsNguoiDung()) {
            if (nd.getId().equals(id)) return nd;
        }
        throw khongThayKhach();
    }

    private void kiemTraKhongPhaiAdmin(NguoiDung nd) {
        if ("admin".equals(nd.getVaiTro())
                || (nd.getEmail() != null && adminEmail.equalsIgnoreCase(nd.getEmail().trim()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đây là tài khoản quản trị, không sửa hay xoá ở trang khách hàng được.");
        }
    }

    private ResponseStatusException khongThayKhach() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng.");
    }

    private String hoTenHopLe(String hoTen) {
        if (hoTen == null || hoTen.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập họ và tên");
        }
        return hoTen.trim();
    }

    /**
     * Email của khách: để trống thì NULL, có thì phải đúng dạng và chưa tài khoản nào
     * dùng (kể cả tài khoản đã xoá mềm — cột email là khoá duy nhất của cả bảng).
     *
     * @param boQuaId id của chính khách đang sửa (gửi lại email cũ thì không phải lỗi)
     */
    private String emailHopLe(String email, Long boQuaId) {
        String mail = email == null ? "" : email.trim();
        if (mail.isEmpty()) return null;
        if (!mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) loi400("Email không hợp lệ");
        String thuong = mail.toLowerCase();
        NguoiDung daCo = nguoiDungRepo.findByEmailIgnoreCase(thuong).orElse(null);
        if (daCo != null && (boQuaId == null || !daCo.getId().equals(boQuaId))) {
            loi400("Email này đã được dùng cho tài khoản khác.");
        }
        return thuong;
    }

    private static String chuoi(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String trongThanhNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
