package vn.in3d.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Gửi email OTP qua Gmail SMTP.
 * Cấu hình bằng biến môi trường GMAIL_USER + GMAIL_APP_PASSWORD (xem README).
 * Nếu chưa cấu hình: KHÔNG gửi email, chỉ in mã ra console để lập trình viên thử.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String nguoiGui;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String nguoiGui) {
        this.mailSender = mailSender;
        this.nguoiGui = nguoiGui;
    }

    public boolean daCauHinh() {
        return nguoiGui != null && !nguoiGui.isBlank();
    }

    /** Gửi mã OTP. Trả về true nếu đã gửi email thật. */
    public boolean guiOtp(String email, String ma, int phutHieuLuc) {
        if (!daCauHinh()) {
            System.out.println("\n============================================================");
            System.out.println("[IN3D] CHƯA CẤU HÌNH GMAIL — mã OTP chỉ hiển thị ở đây:");
            System.out.println("       Email : " + email);
            System.out.println("       Mã OTP: " + ma + "   (hiệu lực " + phutHieuLuc + " phút)");
            System.out.println("       Muốn gửi email thật: đặt GMAIL_USER và GMAIL_APP_PASSWORD");
            System.out.println("============================================================\n");
            return false;
        }

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(nguoiGui, "IN3D Store");
            helper.setTo(email);
            helper.setSubject("Mã đăng nhập quản trị IN3D Store: " + ma);
            helper.setText(taoNoiDung(ma, phutHieuLuc), true);
            mailSender.send(mime);
            System.out.println("[IN3D] Đã gửi mã OTP tới " + email);
            return true;
        } catch (Exception e) {
            System.out.println("[IN3D] LỖI gửi email (" + e.getMessage() + "). Mã OTP cho " + email + " là: " + ma);
            return false;
        }
    }

    private String taoNoiDung(String ma, int phut) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:28px;"
                + "border:1px solid #e4e6e9;border-radius:14px\">"
                + "<h2 style=\"margin:0 0 6px 0;color:#161616\">IN3D Store</h2>"
                + "<p style=\"color:#6b7280;margin:0 0 22px 0\">Hệ thống quản trị bán hàng máy in 3D</p>"
                + "<p style=\"color:#1c2024\">Mã xác thực đăng nhập trang quản trị của bạn là:</p>"
                + "<div style=\"font-size:34px;font-weight:700;letter-spacing:8px;color:#059669;"
                + "background:#d1fae5;border-radius:12px;padding:16px;text-align:center;margin:16px 0\">"
                + ma + "</div>"
                + "<p style=\"color:#6b7280;font-size:14px\">Mã có hiệu lực trong <strong>" + phut
                + " phút</strong> và chỉ dùng được một lần.</p>"
                + "<p style=\"color:#ef4444;font-size:13px\">Nếu bạn không thực hiện đăng nhập, hãy bỏ qua email này "
                + "và đổi mật khẩu quản trị ngay.</p>"
                + "</div>";
    }
}
